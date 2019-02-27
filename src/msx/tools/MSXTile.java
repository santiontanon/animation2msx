/*
 * Santiago Ontanon Villar.
 */
package msx.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author santi
 */
public class MSXTile {
    public int pixels[] = new int[8*8];
    
    
    public MSXTile()
    {
    }


    public MSXTile(int a_pixels[])
    {
        pixels = a_pixels;
    }
    
    
    public double distance(MSXTile t2, EightBitConverter c) {
        double d = 0;
        for(int i = 0;i<pixels.length;i++) {
            d += EightBitConverter.euclideanError(c.palette[pixels[i]], c.palette[t2.pixels[i]]);
        }
        return d;
    }
    

    /* 
    returns a 16 byte array:
        - the first 8 are the pattenr data
        - the second 8 are the attribute data
    */
    public int[] patternBytes()
    {
        int bytes[] = new int[16];
        for(int j = 0;j<8;j++) {
            List<Integer> colors = new ArrayList<>();
            for(int k = 0;k<8;k++) {
                if (!colors.contains(pixels[j*8+k])) colors.add(pixels[j*8+k]);
            }
            while(colors.size()<2) colors.add(0);
            if (colors.size()>2) {
                System.err.println("more than 2 colors in an 8x1 block!!! " + colors);
                System.exit(1);
            }
            Collections.sort(colors);
            int colorbyte = colors.get(0) + colors.get(1)*16;                    
            int patternbyte = 0;
            int mask = 1;
            for(int k = 0;k<8;k++) {
                //System.out.println("  " + tile[j*8+k]);
                if (colors.get(0) != pixels[j*8+(7-k)]) patternbyte += mask;
                mask *= 2;
            }
            bytes[j] = patternbyte;
            bytes[j+8] = colorbyte;
        }        
        
        return bytes;
    }
}
