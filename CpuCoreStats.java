import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.time.LocalDateTime; // Import the LocalDateTime class
import java.time.format.DateTimeFormatter; // Import the DateTimeFormatter class
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CpuCoreStats
{
  private long[] prev_total;
  private long[] prev_idle;
  private long[] prev_io;
  private long[] prev_busy;
  private int core_count;
  static boolean csv = false;
  static int interval_seconds = 1;

  public CpuCoreStats()
  {
    core_count = Runtime.getRuntime().availableProcessors();
    System.out.println("Number of cores: " + core_count);
    prev_total = new long[core_count+1];
    prev_idle = new long[core_count+1];
    prev_io = new long[core_count+1];
    prev_busy = new long[core_count+1];
  }

  public List<String> readFullFile(String fileName)
  {
 
    List<String> lines = Collections.emptyList();
    try
    {
      lines =
       Files.readAllLines(Paths.get(fileName), StandardCharsets.UTF_8);
    }
    catch (IOException e)
    {
      // If we can't read /proc/stat we have bigger problems.
      e.printStackTrace();
    }
    return lines;
  }

  public static void printUsage()
  {
    System.out.println("Syntax: java CpuCoreStats [-csv] [<seconds]");
    System.out.println("     -csv : Display output in CSV compatible format for spreadsheets or database import.");
    System.out.println("<seconds> : Set the interval of seconds to pause between each refresh. Default: 1");
  }

  public static void parseArgs(String[] args)
  {
    for(String arg : args)
    {
      switch(arg)
      {
        case "-h":
	case "-help":
        case "--help":
	  printUsage();
	  System.exit(0);

	case "-csv":
	  csv = true;
	  break;
	default:
	  try
	  {
	    interval_seconds = Integer.parseInt(arg);
	  }
	  catch(NumberFormatException e)
	  {
	    System.err.println("Invalid argument passed. Valid options are: -csv to display data in CSV format or <seconds> to set the delay interval.");
	    printUsage();
	    System.exit(1);
	  }
      }
    }
  }

  public static void main(String[] args) throws Exception
  {
    parseArgs(args);
    CpuCoreStats instance = new CpuCoreStats();
    ScheduledExecutorService executorService;
    executorService = Executors.newSingleThreadScheduledExecutor();
    executorService.scheduleAtFixedRate(instance::runCpuReport, 0, interval_seconds, TimeUnit.SECONDS);
  }

  public void runCpuReport() //throws InterruptedException
  {
    LocalDateTime myDateObj = LocalDateTime.now();
    List proc_stat = readFullFile("/proc/stat");
    ArrayList cpu_cores = new ArrayList<long[]>();
    
    Iterator<String> itr = proc_stat.iterator();
    while (itr.hasNext())
    {
      String line = itr.next();
      if(!line.startsWith("cpu"))
        break;
      long[] cols = Arrays.stream(line.split("\\s+")).filter((s) -> s.matches("\\d+")).mapToLong(Long::parseLong).toArray();
      cpu_cores.add(cols);
    }
    if(cpu_cores.size() > prev_total.length)
    {
      prev_total = new long[cpu_cores.size()];
      prev_idle = new long[cpu_cores.size()];
      prev_io = new long[cpu_cores.size()];
      prev_busy = new long[cpu_cores.size()];
      System.out.println(" *** WARNING : Number of cores available to container are lower than what's available on the host!");
      System.out.println(" *** Cores available to container = " + core_count + ", Cores available on host: " + (cpu_cores.size() - 1));
    }
    DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    String formattedDate = myDateObj.format(myFormatObj);
    if(!csv)
    {
      if(cpu_cores.size() - 1 > core_count)
	System.out.println(" *** Cores available to container = " + core_count + ", Cores available on host: " + (cpu_cores.size() - 1));
      System.out.println(formattedDate);
    }
    for (int i = 0; i < cpu_cores.size(); i++)
    {
      long[] line = (long[]) cpu_cores.get(i);
      long total_time = Arrays.stream(line).sum();
      long idle_time = line[3];
      long io_time = line[4];
      long busy_time = total_time - idle_time;
      long total_diff = total_time - prev_total[i];
      long idle_diff = idle_time - prev_idle[i];
      long io_diff = io_time - prev_io[i];
      long busy_diff = busy_time - prev_busy[i];
      if(csv)
	System.out.printf("%s, %3d, %6.2f,%6.2f%n", formattedDate, i, (double) busy_diff * 100 / (double) total_diff, (double) io_diff * 100 / (double) total_diff);
      else
        System.out.printf("cpu%3d: Busy: %6.2f%%, iowait: %6.2f%%%n", i, (double) busy_diff * 100 / (double) total_diff, (double) io_diff * 100 / (double) total_diff);
      prev_total[i] = total_time;
      prev_idle[i] = idle_time;
      prev_io[i] = io_time;
      prev_busy[i] = busy_time;
    }
  }
}
