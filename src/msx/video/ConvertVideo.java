/*
 * Santiago Ontanon Villar.
 */
package msx.video;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import msx.tools.EightBitConverter;
import msx.tools.KMedoids;
import msx.tools.MSXTile;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 *
 * @author santi
 * 
 * TODO:
 * - Upload to GitHub and use it to update the XRacing build!
 * 
 */
public class ConvertVideo {

    public static class Configuration {
        String inputGifFileName = null;
        String outputFolder = null;
        String paletteFileName = null;
        int startingTile = 0;
        int endingTile = 255;
        double tolerance = 0;
        int halts = 2;
        int frameStride = 1;
        boolean useWeightsForClustering = true;
        int uniformAttributes[] = null;
        int sourceRegion[] = null;
        int targetRegion[] = {0,0,256,192};
    };
    
    
    public static class MSXTileCount {
        MSXTile tile = null;
        int count = 0;
        
        public MSXTileCount(MSXTile a_tile) {
            tile = a_tile;
            count = 1;
        }
    }
    
    
    public static void main(String args[]) throws Exception
    {
        // Process the input arguments:
        Configuration config = readCommandLineParameters(args);
        if (config == null) {
            printHelp();
            return;
        }
        
        // If there is a different palette, read it:
        EightBitConverter converter = EightBitConverter.getMSXConverter();
        if (config.paletteFileName != null) {
            int palette[][] = readColorPalette(config.paletteFileName);
            converter = new EightBitConverter(palette, 2, 8, 1);
            System.out.println("8-bit converter created with custom palette from " + config.paletteFileName);
        }
        
        // Read the animation frames:
        List<BufferedImage> frames = readAnimationFrames(config);
        System.out.println(frames.size() + " animation frames ("+frames.get(0).getWidth()+"x"+frames.get(0).getHeight()+" pixels) read from file " + config.inputGifFileName);
        
        if (config.sourceRegion == null) {
            config.sourceRegion = new int[]{0, 0, frames.get(0).getWidth(), frames.get(0).getHeight()};
        }
        
        List<BufferedImage> frames2 = new ArrayList<>();
        for(int i = 0;i<frames.size();i+=config.frameStride) {
            BufferedImage img = new BufferedImage(256, 192, BufferedImage.TYPE_INT_ARGB);
            img.getGraphics().drawImage(frames.get(i), 
                                        config.targetRegion[0], config.targetRegion[1],
                                        config.targetRegion[2], config.targetRegion[3],
                                        config.sourceRegion[0], config.sourceRegion[1],
                                        config.sourceRegion[2], config.sourceRegion[3], null);                    
            BufferedImage img2 = converter.convertImage(img);
            frames2.add(img2);
        }
        frames = frames2;
        
        // Find all the tiles for each bank:
        if (config.uniformAttributes != null) {
            System.out.println("Converting to uniform attributes...");
            for(BufferedImage img:frames) {
                BufferedImage img2 = new BufferedImage(256, 192, BufferedImage.TYPE_INT_ARGB);
                converter.convertToGivenColorsWithDithering(img2, img, config.uniformAttributes, 
                                                            config.targetRegion[0], config.targetRegion[1],
                                                            config.targetRegion[2], config.targetRegion[3]);
                frames.set(frames.indexOf(img), img2);
            }
        }
            
        System.out.println("Finding tiles in the "+frames.size()+" animation frames...");
        List<List<MSXTileCount>> tiles = new ArrayList<>();
        for(int i = 0;i<3;i++) {
            int bank_starty = i*8*8;
            int bank_endy = (i+1)*8*8;
            if (bank_starty >= config.targetRegion[3] ||
                bank_endy <= config.targetRegion[1]) {
                List<MSXTileCount> bankTiles = new ArrayList<>();
                tiles.add(bankTiles);
                System.out.println("  Bank " + (i+1) + " does not overlap with the target area");
            } else {
                if (config.targetRegion[1] > bank_starty) bank_starty = config.targetRegion[1];
                if (config.targetRegion[3] < bank_endy) bank_endy = config.targetRegion[3];
                System.out.println("  Overlap area is " + config.targetRegion[0] + "," + bank_starty + " - " +
                                                          config.targetRegion[2] + "," + bank_endy);
                List<MSXTileCount> bankTiles = findAllTilesInRegion(frames, converter, config, 
                                                                    config.targetRegion[0], bank_starty, 
                                                                    config.targetRegion[2], bank_endy);
                tiles.add(bankTiles);
                System.out.println("  Found " + bankTiles.size() + " tiles needed for bank " + (i+1));
            }
        }
        
        // Clustering:
        List<List<MSXTile>> clusters = new ArrayList<>();
        for(int i = 0;i<3;i++) {
            int nTiles = (config.endingTile - config.startingTile)+1;
            if (nTiles<tiles.get(i).size()) {
                System.out.println("Reducing from " + tiles.get(i).size() + " to " + nTiles + " ...");
                List<MSXTile> bankClusters = cluster(tiles.get(i), nTiles, converter, config.useWeightsForClustering);
                clusters.add(bankClusters);
            } else {
                System.out.println("No need to reduce, we have only " + nTiles + " tiles!s");
                List<MSXTile> tmp = new ArrayList<>();
                for(MSXTileCount tc:tiles.get(i)) {
                    tmp.add(tc.tile);
                }
                clusters.add(tmp);
            }
        }
        
        // Convert all the frames to thereduced set of tiles:
        List<int []> nameTables = new ArrayList<>();
        for(BufferedImage frame:frames) {
            nameTables.add(convertImageFrame(frame, clusters, converter, config));
        }
        
        // Generate all the MSX files:
        System.out.println("Generating output files to "+config.outputFolder+"...");
        createFolderIfItDoesNotExist(config.outputFolder);
        MSXAnimationROM.generateAssemblerFiles(nameTables, clusters, config);
        MSXAnimationROM.generateROM(nameTables, clusters, config);
    }
    
    
    public static Configuration readCommandLineParameters(String args[]) throws Exception
    {
        List<String> optionPrefixes = new ArrayList<>();
        optionPrefixes.add("-p");
        optionPrefixes.add("-r");
        optionPrefixes.add("-d");
        optionPrefixes.add("-s");
        optionPrefixes.add("-nw");
        optionPrefixes.add("-sa");
        optionPrefixes.add("-ta");
        optionPrefixes.add("-c");
        int firstOptionParameter = 1;
        Configuration config = new Configuration();
        
        // get input file name:
        if (args.length < 1) return null;
        config.inputGifFileName = args[0];
        
        // get output folder:
        if (args.length > 1 && !optionPrefixes.contains(args[1])) {
            // output folder is specified:
            config.outputFolder = args[1];
            if (!config.outputFolder.endsWith(File.separator)) config.outputFolder += File.separator;
            firstOptionParameter = 2;
        } else {
            // use the same folder as the input file:
            int idx = config.inputGifFileName.lastIndexOf(File.separator);
            if (idx == -1) {
                config.outputFolder = "." + File.separator;
            } else {
                config.outputFolder = config.inputGifFileName.substring(0, idx) + File.separator;
            }
        }
        
        // get the additional options:
        while(firstOptionParameter < args.length) {
            int option = optionPrefixes.indexOf(args[firstOptionParameter]);
            switch(option) {
                case 0: // -p
                    if (args.length <= firstOptionParameter+1) return null;
                    config.paletteFileName = args[firstOptionParameter+1];
                    firstOptionParameter+=2;
                    break;
                case 1: // -r
                    {
                        if (args.length <= firstOptionParameter+1) return null;
                        StringTokenizer st = new StringTokenizer(args[firstOptionParameter+1],"-");
                        config.startingTile = Integer.parseInt(st.nextToken());
                        config.endingTile = Integer.parseInt(st.nextToken());
                        firstOptionParameter+=2;
                    }
                    break;
                case 2: // -d
                    if (args.length <= firstOptionParameter+1) return null;
                    config.halts = Integer.parseInt(args[firstOptionParameter+1]);
                    firstOptionParameter+=2;
                    break;
                case 3: // -s
                    if (args.length <= firstOptionParameter+1) return null;
                    config.frameStride = Integer.parseInt(args[firstOptionParameter+1]);
                    firstOptionParameter+=2;
                    break;
                case 4: // -nw
                    config.useWeightsForClustering = false;
                    firstOptionParameter++;
                    break;
                case 5: // -sa
                    {
                        if (args.length <= firstOptionParameter+1) return null;
                        StringTokenizer st = new StringTokenizer(args[firstOptionParameter+1],",");
                        config.sourceRegion = new int[4];
                        config.sourceRegion[0] = Integer.parseInt(st.nextToken());
                        config.sourceRegion[1] = Integer.parseInt(st.nextToken());
                        config.sourceRegion[2] = Integer.parseInt(st.nextToken());
                        config.sourceRegion[3] = Integer.parseInt(st.nextToken());
                        firstOptionParameter+=2;
                    }
                    break;
                case 6: // -ta
                    {
                        if (args.length <= firstOptionParameter+1) return null;
                        StringTokenizer st = new StringTokenizer(args[firstOptionParameter+1],",");
                        config.targetRegion = new int[4];
                        config.targetRegion[0] = Integer.parseInt(st.nextToken());
                        config.targetRegion[1] = Integer.parseInt(st.nextToken());
                        config.targetRegion[2] = Integer.parseInt(st.nextToken());
                        config.targetRegion[3] = Integer.parseInt(st.nextToken());
                        firstOptionParameter+=2;
                    }
                    break;
                case 7: // -c
                    {
                        if (args.length <= firstOptionParameter+1) return null;
                        StringTokenizer st = new StringTokenizer(args[firstOptionParameter+1],",");
                        config.uniformAttributes = new int[2];
                        config.uniformAttributes[0] = Integer.parseInt(st.nextToken());
                        config.uniformAttributes[1] = Integer.parseInt(st.nextToken());
                        firstOptionParameter+=2;
                    }
                    break;
                default:
                    return null;
            }
        }
        
        return config;
    }
    
    public static void printHelp()
    {
        System.out.println("MSX Video Converter by Santiago Ontanon Villar (2019)");
        System.out.println("Questions/comments: santi.ontanon@gmail.com, or @santiontanonMSX in Twitter");
        System.out.println("");
        
        System.out.println("Arguments:");
        System.out.println("    - input gif file (e.g. examples/flag.gif)");
        System.out.println("    - [optional] output folder (e.g. documents/my-msx-flag). \n" +
                           "          If this is not specified, all output files will be generated in the same folder\n" +
                           "          as the input gif file");
        System.out.println("Options:");
        System.out.println("    -p [filename]: to specify a different MSX palette than the default one. \n" +
                           "            See the examples folder for an example of how to specify a palette in a\n" +
                           "            csv/tsv file (one color per row with 3 columns per row with the RGB values in\n" +
                           "            the 0 - 255 range, separated by spaces, commas or tabs).");
        System.out.println("    -r [tile range to use]: by default, all 256 (tiles from 0 to 255) tiles in each \n" +
                           "            of the 3 banks will be used. However, if a smaller range is desired, this\n" +
                           "            can be specified with the -tr option. For example -tr 32-127 would force the\n" +
                           "            program to only use tiles from 32-127 (96 tiles).");
        System.out.println("    -d [delay]: the number of halt instructions between animation frames in the" +
                           "            generated rom file. The default is 2");
        System.out.println("    -s [stride]: by default, all the frames in the gif file will be used. However,\n" +
                           "            some gif files are long. So, you can specify if you want to skip frames.\n" +
                           "            By default, the stride is set to 1 (use every frame), but you can set it\n" +
                           "            to 2 (skip every other frame), 3, etc. Also, notice that if the video has\n" + 
                           "            more than 16 frames, the demo ROM will not work, since the nameTables will\n" +
                           "            not fit in memory.");
        System.out.println("    -nw: ignore the number of times each tile appears in the video during clustering.");
        System.out.println("    -sa x1,y1,x2,y2: source area to capture from the gif (default is the whole screen");
        System.out.println("    -ta x1,y1,x2,y2: target area of the MSX screen (default is 0,0,256,192)");
        System.out.println("    -c c1,c2: generate the animation using only two colors (c1,c2) for the\n" +
                           "         whole animation. This saves space in the ROM, since we do not need to store\n" +
                           "         the attributes table of the tiles. For example, to generate an animation in\n"+
                           "         black and white, use -ua 0,15");
        System.out.println("");
        System.out.println("Example:");
        System.out.println("    java -jar MSXVideoConverter.jar examples/flag.gif examples/flag -p examples/msxpalette.tsv");        
    }
    
    
    public static int[][] readColorPalette(String paletteFileName) throws Exception
    {
        int palette[][] = new int[16][3];
        
        BufferedReader br = new BufferedReader(new FileReader(paletteFileName));
        for(int i = 0;i<16;i++) {
            StringTokenizer st = new StringTokenizer(br.readLine(), " ,\t");
            palette[i][0] = Integer.parseInt(st.nextToken());
            palette[i][1] = Integer.parseInt(st.nextToken());
            palette[i][2] = Integer.parseInt(st.nextToken());
        }
        
        return palette;
        
    }


    // Source of this GIF reading routine: https://stackoverflow.com/questions/8933893/convert-each-animated-gif-frame-to-a-separate-bufferedimage#16234122
    private static List<BufferedImage> readAnimationFrames(Configuration config) throws Exception {
        List<BufferedImage> frames = new ArrayList<>();

        String[] imageatt = new String[]{
                "imageLeftPosition",
                "imageTopPosition",
                "imageWidth",
                "imageHeight"
        };    

        ImageReader reader = (ImageReader)ImageIO.getImageReadersByFormatName("gif").next();
        ImageInputStream ciis = ImageIO.createImageInputStream(new File(config.inputGifFileName));
        reader.setInput(ciis, false);

        int noi = reader.getNumImages(true);
        BufferedImage master = null;
        BufferedImage currentFrame = null;

        for (int i = 0; i < noi; i++) { 
            BufferedImage image = reader.read(i);
            IIOMetadata metadata = reader.getImageMetadata(i);

            Node tree = metadata.getAsTree("javax_imageio_gif_image_1.0");
            NodeList children = tree.getChildNodes();
            master = null;
            
            for (int j = 0; j < children.getLength(); j++) {
                Node nodeItem = children.item(j);

                if(nodeItem.getNodeName().equals("ImageDescriptor")){
                    Map<String, Integer> imageAttr = new HashMap<>();

                    for (int k = 0; k < imageatt.length; k++) {
                        NamedNodeMap attr = nodeItem.getAttributes();
                        Node attnode = attr.getNamedItem(imageatt[k]);
                        imageAttr.put(imageatt[k], Integer.valueOf(attnode.getNodeValue()));
                    }
                    if(master == null) {
                        master = new BufferedImage(imageAttr.get("imageWidth"), imageAttr.get("imageHeight"), BufferedImage.TYPE_INT_ARGB);
                    }
                    master.getGraphics().drawImage(image, imageAttr.get("imageLeftPosition"), imageAttr.get("imageTopPosition"), null);
                }
            }
            currentFrame = new BufferedImage(master.getWidth(), master.getHeight(), BufferedImage.TYPE_INT_ARGB);
            currentFrame.getGraphics().drawImage(master, 0, 0, null);
            frames.add(currentFrame);
        }

        return frames;
    }

    
    private static List<MSXTileCount> findAllTilesInRegion(List<BufferedImage> frames, EightBitConverter converter, 
            Configuration config, int x1, int y1, int x2, int y2) {
        List<MSXTileCount> tiles = new ArrayList<>();
        
        for(BufferedImage img:frames) {
            for(int y=y1;y<y2;y+=8) {
                for(int x=x1;x<x2;x+=8) {
                    MSXTile tile = converter.getTile(x, y, img);
                    boolean found = false;
                    for(MSXTileCount tile2:tiles) {
                        if (tile.distance(tile2.tile, converter) <= config.tolerance) {
                            found = true;
                            tile2.count++;
                            break;
                        }
                    }
                    if (!found) tiles.add(new MSXTileCount(tile));
                }        
            }
        }
        
        return tiles;
    }
    
    
    public static List<MSXTile> cluster(List<MSXTileCount> tiles, int k, EightBitConverter converter, boolean useWeights)
    {
        List<MSXTile> clusters = new ArrayList<>();

        System.out.println("  Calculating the distance matrix...");
        double m[][] = new double[tiles.size()][tiles.size()];
        double weights[] = new double[tiles.size()];
        for(int i = 0;i<tiles.size();i++) {
            m[i][i] = 0;
            if (useWeights) {
                weights[i] = tiles.get(i).count;
            } else {
                weights[i] = 1;
            }
            for(int j = i+1;j<tiles.size();j++) {
                m[i][j] = tiles.get(i).tile.distance(tiles.get(j).tile, converter);
                m[j][i] = m[i][j];
            }
        }

        // k-means (256 clusters):
        System.out.println("  Running clustering...");
        int medoids[] = KMedoids.kMedoids(m, weights, k);
        
        for(int i = 0;i<medoids.length;i++) {
            clusters.add(tiles.get(medoids[i]).tile);
        }
        
        return clusters;
    }    


    // Note: this function assumes the frames are 256x192 pixels and that clusters is
    // of length 3 (one set of tiles per bank)
    private static int[] convertImageFrame(BufferedImage frame, List<List<MSXTile>> clusters, EightBitConverter converter, Configuration config) {
        int startx = 0, endx = 32;
        int starty = 0, endy = 24;
        
        if (config.targetRegion != null) {
            startx = config.targetRegion[0]/8;
            starty = config.targetRegion[1]/8;
            endx = (config.targetRegion[2]+7)/8;
            endy = (config.targetRegion[3]+7)/8;
        }
        int width = endx-startx;
        int height = endy-starty;
        int nameTable[] = new int[width*height];
        
        for(int bank = 0;bank<3;bank++) {
            for(int y = 0;y<8;y++) {
                int screeny = bank*8 + y;
                if (screeny >= starty && screeny < endy) {
                    for(int x = startx;x<endx;x++) {
                        MSXTile tile = converter.getTile(x*8, (bank*8+y)*8, frame);
                        MSXTile bestTile = null;
                        double bestDistance = 0;
                        for(MSXTile tile2:clusters.get(bank)) {
                            double d = tile.distance(tile2, converter);
                            if (bestTile == null || d < bestDistance) {
                                bestTile = tile2;
                                bestDistance = d;
                            }
                        }
                        nameTable[x+(bank*8+y)*width] = clusters.get(bank).indexOf(bestTile);
                    }
                }
            }
        }
        
        return nameTable;
    }

    
    private static void createFolderIfItDoesNotExist(String outputFolder) throws Exception
    {
        File directory = new File(outputFolder);
        if (!directory.exists()) directory.mkdirs();  
    }    
}
