/*
 * Santiago Ontanon Villar.
 */
package msx.tools;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;
import java.awt.image.BufferedImage;

/**
 *
 * @author santi
 */
public class EightBitConverter {

    public int palette[][];
    int maxColorsPerAttributeBlock = 2;
    int attributeBlockWidth = 8;
    int attributeBlockHeight = 8;
    
    
    public static int ALPHA_MASK = 0xff000000;
    public static int R_MASK = 0x00ff0000;
    public static int G_MASK = 0x0000ff00;
    public static int B_MASK = 0x000000ff;
    
    public static int ALPHA_SHIFT = 24;
    public static int R_SHIFT = 16;
    public static int G_SHIFT = 8;
    public static int B_SHIFT = 0;
    
    /*
    public static int MSX1Palette[][] = {
                                {0,0,0},
                                {0,0,0},
                                {43,221,81},
                                {100,255,118},
                                {81,81,255},
                                {118,118,255},
                                {221,81,81},
                                {81,255,255},
                                {255,81,81},
                                {255,118,118},
                                {255,221,81},
                                {255,255,160},
                                {43,187,43},
                                {221,81,187},
                                {221,221,221},
                                {255,255,255}};     
    */
    
    public static int MSX1Palette[][]={
                                {0,0,0},              // Transparent
                                {0,0,0},              // Black
                                {36,219,36},          // Medium Green
                                {109,255,109},        // Light Green
                                {36,36,255},          // Dark Blue
                                {73,109,255},         // Light Blue
                                {182,36,36},          // Dark Red
                                {73,219,255},         // Cyan
                                {255,36,36},          // Medium Red
                                {255,109,109},        // Light Red
                                {219,219,36},         // Dark Yellow
                                {219,219,146},        // Light Yellow
                                {36,146,36},          // Dark Green
                                {219,73,182},         // Magenta
                                {182,182,182},        // Grey
                                {255,255,255}};       // White        
    
    
    public static EightBitConverter getMSXConverter() {      
        return new EightBitConverter(MSX1Palette, 2, 8, 1);
    }    
        

    public EightBitConverter(int a_palette[][], int cpab, int abw, int abh) {
        palette = a_palette;
        maxColorsPerAttributeBlock = cpab;
        attributeBlockWidth = abw;
        attributeBlockHeight = abh;
    }


    public BufferedImage convertImage(BufferedImage sp) {
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        BufferedImage i = gc.createCompatibleImage(sp.getWidth(), sp.getHeight(), Transparency.BITMASK);
        for(int by = 0;by<sp.getHeight();by+=attributeBlockHeight) {
            for(int bx = 0;bx<sp.getWidth();bx+=attributeBlockWidth) {
                int chosenColors[] = new int[maxColorsPerAttributeBlock];
                int colors[] = new int[maxColorsPerAttributeBlock];
                double bestError = -1;
                for(int c = 0;c<maxColorsPerAttributeBlock;c++) colors[c] = c;
                do{
                    double error = conversionError(sp, colors, bx, by, attributeBlockWidth, attributeBlockHeight);
                    if (bestError==-1 || error<bestError) {
                        for(int c = 0;c<maxColorsPerAttributeBlock;c++) chosenColors[c] = colors[c];
                        bestError = error;
                    }
                }while(nextColors(colors));
                convertToGivenColorsWithDithering(i, sp, chosenColors, bx, by, attributeBlockWidth, attributeBlockHeight);
            }
        }
        return i;
    }


    boolean nextColors(int colors[]) {
        int c = colors.length-1;
        do {
            int tmp = (colors.length - 1) - c; 
            
            colors[c]++;
            // make sure that they are in ascending order:
            for(int c1 = c+1;c1<colors.length;c1++) {
                colors[c1] = colors[c1-1]+1;
            }
            if (colors[c]>=palette.length-tmp) {
                colors[c] = 0;
                c--;
                if (c<0) return false;
            } else {
                return true;
            }
        }while(true);        
    }


    public void convertToGivenColors(BufferedImage outputImage, BufferedImage inputImage, int colors[], int x0, int y0, int w, int h) {
        for(int y = y0;y<y0+h;y++) {
            for(int x = x0;x<x0+w;x++) {
                int color = inputImage.getRGB(x, y);
                int a = (color & ALPHA_MASK)>>ALPHA_SHIFT;
                int rgb[] = {(color & R_MASK)>>R_SHIFT, (color & G_MASK)>>G_SHIFT, (color & B_MASK)>>B_SHIFT};
                int bestc = -1;
                double besterror = -1;

                for(int idx = 0;idx<colors.length;idx++) {
                    int c = colors[idx];
                    double e = euclideanError(palette[c],rgb);
                    if (besterror==-1 || e<besterror) {
                        bestc = c;
                        besterror = e;
                    }
                }
                outputImage.setRGB(x, y, palette[bestc][2] + (palette[bestc][1]<<8) + (palette[bestc][0]<<16) + (a<<24));
            }
        }
    }

    
    public void convertToGivenColorsWithDithering(BufferedImage outputImage, BufferedImage inputImage, int colors[], int x0, int y0, int w, int h) {
        for(int y = y0;y<y0+h;y++) {
            double previous_besterror = 0;
            int previous_bestc = -1;
            for(int x = x0;x<x0+w;x++) {
                int color = inputImage.getRGB(x, y);
                int a = (color & ALPHA_MASK)>>ALPHA_SHIFT;
                int rgb[] = {(color & R_MASK)>>R_SHIFT, (color & G_MASK)>>G_SHIFT, (color & B_MASK)>>B_SHIFT};
                int bestc = -1;
                double besterror = -1;

                for(int idx = 0;idx<colors.length;idx++) {
                    int c = colors[idx];
                    double e = euclideanError(palette[c],rgb);
                    if (besterror==-1 || e<besterror) {
                        bestc = c;
                        besterror = e;
                    }
                }
                
                outputImage.setRGB(x, y, palette[bestc][2] + (palette[bestc][1]<<8) + (palette[bestc][0]<<16) + (a<<24));

                // dithering:
                if ((x%2 == 1) && colors.length == 2 &&
                    bestc == previous_bestc) {
                    int color_prev = inputImage.getRGB(x, y);
                    int rgb_prev[] = {(color_prev & R_MASK)>>R_SHIFT, (color_prev & G_MASK)>>G_SHIFT, (color_prev & B_MASK)>>B_SHIFT};
                    int blend[] = {(palette[colors[0]][0]+palette[colors[1]][0])/2, 
                                   (palette[colors[0]][1]+palette[colors[1]][1])/2,
                                   (palette[colors[0]][2]+palette[colors[1]][2])/2};
                    double e2 = euclideanError(blend,rgb_prev);
                    if (e2 < (besterror+previous_besterror)/2) {
                        if ((y%2) == 0) {
                            outputImage.setRGB(x-1, y, palette[colors[0]][2] + (palette[colors[0]][1]<<8) + (palette[colors[0]][0]<<16) + (a<<24));
                            outputImage.setRGB(x, y, palette[colors[1]][2] + (palette[colors[1]][1]<<8) + (palette[colors[1]][0]<<16) + (a<<24));
                        } else {
                            outputImage.setRGB(x-1, y, palette[colors[1]][2] + (palette[colors[1]][1]<<8) + (palette[colors[1]][0]<<16) + (a<<24));
                            outputImage.setRGB(x, y, palette[colors[0]][2] + (palette[colors[0]][1]<<8) + (palette[colors[0]][0]<<16) + (a<<24));                            
                        }
                    }
                }
                
                previous_besterror = besterror;
                previous_bestc = bestc;
            }
        }
    }    
    
    
    public int[] bestColorsForBlock(BufferedImage img, int x1, int y1, int x2, int y2)
    {
        int chosenColors[] = new int[maxColorsPerAttributeBlock];
        int colors[] = new int[maxColorsPerAttributeBlock];
        double bestError = -1;
        for(int c = 0;c<maxColorsPerAttributeBlock;c++) colors[c] = c;
        do{
            double error = conversionError(img, colors, x1, y1, x2-x1, y2-y1);
            if (bestError==-1 || error<bestError) {
                for(int c = 0;c<maxColorsPerAttributeBlock;c++) chosenColors[c] = colors[c];
                bestError = error;
            }
        }while(nextColors(colors));
        
        return chosenColors;
    }
    
    
    public MSXTile getTile(int x, int y, BufferedImage img)
    {
        int []tile = new int[64];
        
        for(int y0 = 0;y0<8;y0++) {
            for(int x0 = 0;x0<8;x0++) {
                int color = img.getRGB(x+x0, y+y0);
                int rgb[] = {(color & R_MASK)>>R_SHIFT, (color & G_MASK)>>G_SHIFT, (color & B_MASK)>>B_SHIFT};
                tile[x0+y0*8] = getClosestColor(rgb);
            }        
        }
        
        return new MSXTile(tile);
    }    
        

    public static double euclideanError(int c1[], int c2[]) {
        double e1 = Math.sqrt((c1[0]-c2[0])*(c1[0]-c2[0]) +
                              (c1[1]-c2[1])*(c1[1]-c2[1]) +
                              (c1[2]-c2[2])*(c1[2]-c2[2]));
        return e1;
    }


    double colorError(int c1[], int c2[]) {
        double norm1 = Math.sqrt(c1[0]*c1[0] + c1[1]*c1[1] + c1[2]*c1[2]);
        double norm2 = Math.sqrt(c2[0]*c2[0] + c2[1]*c2[1] + c2[2]*c2[2]);
        double dotProduct = c1[0]*c2[0] + c1[1]*c2[1] + c1[2]*c2[2];

        if (norm1==0 && norm2!=0) return 4; // max error
        if (norm1!=0 && norm2==0) return 4; // max error
        if (norm1==0 && norm2==0) return 0;
        
        double cosine = dotProduct/(norm1*norm2);

        double errorAngle = 1 - cosine*cosine;
        double errorMagnitude = Math.abs(norm1-norm2)/Math.sqrt(255*255 + 255*255 + 255*255);
        double e = errorAngle + errorMagnitude;

        return e;
    }
  

    double conversionError(BufferedImage sp, int chosenColors[], int x0, int y0, int w, int h) {
        double error = 0;
        for(int y = y0;y<y0+h;y++) {
            for(int x = x0;x<x0+w;x++) {
                int color = sp.getRGB(x, y);
                int rgb[] = {(color & R_MASK)>>R_SHIFT, (color & G_MASK)>>G_SHIFT, (color & B_MASK)>>B_SHIFT};
                double e = -1;
                for(int c:chosenColors) {
                    double e1 = euclideanError(palette[c], rgb);
                    if (e==-1 || e1<e) e = e1;
                }
                error += e;
            }
        }
        return error;
    }
    
    
    public int getClosestColor(int rgb[])
    {
        int bestc = -1;
        double best_e = 0;
        for(int c = 0;c<palette.length;c++) {
            double e = euclideanError(palette[c], rgb);
            if (bestc == -1 || e < best_e) {
                bestc = c;
                best_e = e;
            }
        }
        return bestc;
    }

}
