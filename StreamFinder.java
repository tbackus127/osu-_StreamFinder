//=============================================================================================================================================================
// osu! Stream Finder
//-------------------------------------------------------------------------------------------------------------------------------------------------------------
// Author: Rathuldr (rathuldr@gmail.com)
// Date: 6/28/15
//=============================================================================================================================================================

import java.util.Scanner;
import java.util.ArrayList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

//-------------------------------------------------------------------------------------------------------------------------------------------------------------
// Usage: java StreamFinder
// This will by default create a new file, streamyMaps.txt, containing a list of all beatmaps in your osu! directory that contain streams.
//-------------------------------------------------------------------------------------------------------------------------------------------------------------

public class StreamFinder {
  
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  // Things to be configured:
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  
  // The path to your osu! songs directory (use two backslashes for directories)
  private static final String OSU_DIR = "C:\\Program Files (x86)\\osu!\\Songs";
  
  // Filename to output list of streams to (uses same directory as the program was ran from)
  private static final String OUTPUT_FILE = "streamyMaps.txt";
  
  // How many consecutive circles needed to be counted as a stream
  private static final int THRESHOLD_STREAM = 13;
  
  // Leniency in ms to be considered a stream (to help with floating-point rounding)
  private static final int THRESHOLD_VARIANCE = 3;
  
  // Enable debug mode (Text EVERYWHERE, I don't recommend turning this on)
  private static final boolean DEBUG = false;
  
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  
  // Double[2]: [Offset in ms, ms per beat at that offset]
  private static ArrayList<Double[]> timingPoints;
  
  private static ArrayList<Double> hitCircles;
  private static PrintStream outputStream;
  
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  /**
   * Main method
   * @param args runtime arguments (not used)
  */
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  public static void main(String[] args) {
    System.out.println("Finding streams...");
    File[] beatmapSets = new File(OSU_DIR).listFiles();
    try {
      outputStream = new PrintStream(new File(OUTPUT_FILE));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    
    //---------------------------------------------------------------------------------------------------------------------------------------------------------
    // Iterate over all subfolders in osu! directory
    for(int i = 0; i < beatmapSets.length; i++) {
      
      // File handle for mapset folder
      File mapset = beatmapSets[i];
      
      // Only continue if a directory
      if(mapset.isDirectory()) {
        
        // Create list of all files in a mapset
        File[] mapsetFiles = mapset.listFiles();
        
        // Iterate over all files in mapset's folder
        for(int j = 0; j < mapsetFiles.length; j++) {
          
          // Only continue if it's an .osu file
          if(isOsuBeatmap(mapsetFiles[j]) && isOsuMode(mapsetFiles[j])) {
            if(parseBeatmap(mapsetFiles[j])) {
              findStreams(mapsetFiles[j].getName());
              timingPoints = null;
              hitCircles = null;
            }
          }
        }
      }
    }
  }
  
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  /**
   * Finds streams in already populated timingPoints and hitCircles objects.
   * @param mapName the name of the .osu file (for passing to printStreams())
  */
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  private static void findStreams(String mapName) {
    
    // Check for errors in data structures
    if(timingPoints == null || hitCircles == null || timingPoints.size() < 1 || hitCircles.size() < THRESHOLD_STREAM) {
      dp("!! NO TIMING POINTS OR NO HIT CIRCLES");
    } else {
      dp("Finding streams for " + mapName);
      
      int timingPointNumber = 0;
      int streamCount = 1;
      
      // Double[Stream count, Stream BPM]
      ArrayList<Double[]> streams = new ArrayList<Double[]>();
      
      // Current milliseconds per beat
      double currentMsb = timingPoints.get(timingPointNumber)[1];
      double lastMsb = -1.0;
      
      // How many ms/beat to be considered a stream (16th notes). This poses a problem with maps like The Big Black, where the BPM is double
      // what it's supposed to be.
      double streamThreshold = currentMsb / 4.0;
      double lastCircleTime = -100000.0;
      
      // Flag for whether or not the BPM was changed. There's probably a better way to refractor this...
      boolean bpmChanged = false;
      
      //-------------------------------------------------------------------------------------------------------------------------------------------------------
      // Iterate through all hit circles
      for(int i = 1; i < hitCircles.size(); i++) {
        double circleTime = hitCircles.get(i);
        dp("" + circleTime);
        
        // If we need to change BPM (encounter a new timing point before or at the circle's time)
        if(timingPoints.size() > timingPointNumber + 1 && circleTime >= timingPoints.get(timingPointNumber + 1)[0]) {
          timingPointNumber++;
          dp("Changed MSB from " + currentMsb + " to " + timingPoints.get(timingPointNumber)[1]);
          
          // Refresh the stream threshold and other things
          lastMsb = currentMsb;
          currentMsb = timingPoints.get(timingPointNumber)[1];
          streamThreshold = currentMsb / 4.0;
          bpmChanged = true;
        }
        
        //-----------------------------------------------------------------------------------------------------------------------------------------------------
        // If we've reached the last hit circle, prevent the last note of a map being part of a stream not adding that stream to the list
        if(i+1 == hitCircles.size())
          lastCircleTime = -100000.0;
        
        // If this circle's time difference from the last one is under or at the threshold to be considered a stream, increment the stream counter
        if(circleTime - lastCircleTime <= (streamThreshold + THRESHOLD_VARIANCE)) {
          streamCount++;
          
        // If the stream has ended
        } else {
          
          //---------------------------------------------------------------------------------------------------------------------------------------------------
          // If it's long enough to be considered a stream
          if(streamCount >= THRESHOLD_STREAM) {
            Double[] strm = new Double[2];
            strm[0] = new Double((double)streamCount);
            
            // Use either the current or last BPM depending on whether or not the BPM changed this iteration
            if(bpmChanged)
              strm[1] = new Double(60000.0 / lastMsb);
            else
              strm[1] = new Double(60000.0 / currentMsb);
            
            // Add the stream to the ArrayList
            streams.add(strm);
            dp("Added new stream: " + strm[0] + "x at " + strm[1] + "BPM");
          }
          
          // Reset the stream counter (we've reached the end of a stream)
          streamCount = 1;
        }
        
        // Reset flag and rotate the current circle's time out
        bpmChanged = false;
        lastCircleTime = circleTime;
      }
      
      //-------------------------------------------------------------------------------------------------------------------------------------------------------
      // Print a list of the streams if there are any
      if(streams.size() >= 1)
        printStreams(mapName, streams);
      
    }
  }
  
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  /**
   * Populates timingPoints and hitCircles with the Timing Points and Hit Circles from the passed beatmap.
   * @param beatmap the .osu file to extract data from
   * @return true if parsing is successful, false otherwise
  */
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  private static boolean parseBeatmap(File beatmap) {
    timingPoints = new ArrayList<Double[]>();
    hitCircles = new ArrayList<Double>();
    try {
      
      // Scanner MUST be in UTF-8 mode to prevent parsing errors with Unicode
      Scanner sc = new Scanner(beatmap, "UTF-8");
      
      // 1 = Timing Points, 2 = Hit Objects
      int parsingState = 0;
      while(sc.hasNextLine()) {
        String line = sc.nextLine();
        
        //-----------------------------------------------------------------------------------------------------------------------------------------------------
        // If we find the Timing Points section
        if(line.equals("[TimingPoints]")) {
          parsingState = 1;
          dp("Parsing timing points");
          
        // If we find the Hit Objects section
        } else if(line.equals("[HitObjects]")) {
          parsingState = 2;
          dp("Parsing hit objects");
        } else {
          
          //---------------------------------------------------------------------------------------------------------------------------------------------------
          // Timing Points
          if(parsingState == 1) {
            String[] tokens = line.split(",");
            
            // 8 arguments for every timing point (prevents blank lines from being parsed)
            if(tokens.length == 8) {
              double time = Double.parseDouble(tokens[0]);
              double timing = Double.parseDouble(tokens[1]);
              
              // Only add if it is a change in BPM (relative timing is ignored)
              if(timing >= 0) {
                Double[] timingPoint = new Double[2];
                timingPoint[0] = new Double(time);
                timingPoint[1] = new Double(timing);
                timingPoints.add(timingPoint);
              }
            }
            
          //---------------------------------------------------------------------------------------------------------------------------------------------------
          // Hit Objects
          } else if(parsingState == 2) {
            String[] tokens = line.split(",");
            
            // Line must have 6 or more arguments to be a valid Hit Object
            if(tokens.length >= 6) {
              double time = Double.parseDouble(tokens[2]);
              if(time >= 0) {
                hitCircles.add(time);
              }
            }
          }
        }
      }
      
      //-------------------------------------------------------------------------------------------------------------------------------------------------------
      // If we get through the entire beatmap and populated both timingPoints and hitObjects, we're done
      if(parsingState == 2) {
        return true;
      }
      
      sc.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    
    // If for some reason we didn't correctly parse the beatmap file, return false to prevent printing streams
    return false;
  }
  
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  /**
   * Prints the timing points to the console (used for debug purposes)
  */
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  private static void printTimingPoints() {
    if(timingPoints != null) {
      System.out.println("--TIMING POINTS--\n");
      for(int i = 0; i < timingPoints.size(); i++) {
        double mpb = timingPoints.get(i)[0];
        double bpm = 60000 / timingPoints.get(i)[1];
        System.out.println("Point #" + i + ": T=" + (int)Math.round(mpb) + " " + "BPM=" + (int)Math.round(bpm));
      }
      System.out.println("\n");
    } else {
      System.out.println("null");
    }
  }
  
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  /**
   * Prints a list of streams to the file specified by the global variable OUTPUT_FILE
   * @param mapName the name of the .osu file (used only in outputting the map name)
   * @param streams the ArrayList of streams. Double[2] format: [Stream count, Stream BPM].
  */
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  private static void printStreams(String mapName, ArrayList<Double[]> streams) {
    if(streams == null)
      return;
    
    // Print the beatmap name to the file
    outputStream.println(mapName);
    
    // Go through all of the streams we previously extracted
    for(int i = 0; i < streams.size(); i++) {
      Double[] strm = streams.get(i);
      String count = "" + (int)Math.round(strm[0]);
      String bpm = "" + (int)Math.round(strm[1]);
      
      // Print format example: "21x at 180BPM"
      outputStream.println(count + "x at " + bpm + "BPM");
    }
    outputStream.println("\n");
  }
  
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  /**
   * Checks if a file is an osu! beatmap file
   * @param f the File to check
   * @return true if the file extension is ".osu", false otherwise
  */
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  private static boolean isOsuBeatmap(File f) {
    return (f.getName().endsWith(".osu"));
  }
  
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  /**
   * Checks if the beatmap file is for osu!standard
   * @param beatmap the File to check
   * @return true if the file is for osu!standard (not Taiko, osu!mania, or Catch the Beat; Mode: 0)
  */
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  private static boolean isOsuMode(File beatmap) {
    try {
      Scanner sc = new Scanner(beatmap, "UTF-8");
      
      // Find the line that starts with "Mode:", and check if there's a 0 after it
      while(sc.hasNextLine()) {
        String line = sc.nextLine();
        if(line.startsWith("Mode:")) {
          String[] tokens = line.split(":");
          return (Integer.parseInt(tokens[1].trim()) == 0);
        }
      }
      sc.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    
    // We should only get here if the file is not a valid .osu file.
    return false;
  }
  
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  /**
   * Debug println(). Prints a passed String only if DEBUG is true.
   * @param s the String to print
  */
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------
  private static void dp (String s) {
    if(DEBUG)
      System.out.println(s);
  }
}