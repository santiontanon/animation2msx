/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package msx.tools;

import java.awt.image.BufferedImage;

/**
 *
 * @author santi
 *
 * Adapted from Eric Boez's C adaptation
 * (https://github.com/ericb59/graphxconv/blob/master/MSX1%20Graphic%20Converter/converter.h)
 * of Leandro Correira's original code (https://pastebin.com/1nThpe7j)
 *
 */
public class CIEDE2000 {

    static boolean DITHERING = true;

    static int ORG_X = 0;
    static int ORG_Y = 0;
    static int N_COLORS = 15;   // only 15 colors on MSX (transparend does not count)

    //static int tolerance = 100;
    static int tolerance = 50;
    static int detaillevel = 32;


    public static int[] getRGBA(BufferedImage img, int x, int y) {
        int color = img.getRGB(x, y);
        int r = (color & 0xff0000) >> 16;
        int g = (color & 0x00ff00) >> 8;
        int b = color & 0x0000ff;
        int a = (color & 0xff000000) >> 24;
        return new int[]{r, g, b, a};
    }

    public static double calcdist2000(double r1, double g1, double b1,
            double r2, double g2, double b2) {

        if (r1 == r2 && g1 == g2 && b1 == b2) {
            return 3.0;
        }

        // Convert two RGB color values into Lab and uses the CIEDE2000 formula to calculate the distance between them.
        // This function first converts RGBs to XYZ and then to Lab.
        // This is not optimized, but I did my best to make it readable. In some rare cases there are some weird colors,
        // so MAYBE there's a small bug in the implementation.
        // The RGB to Lab conversion in here could easily be substituted by a giant RGB to Lab lookup table,
        // consuming much more memory, but gaining A LOT in speed.
        //	Converting RGB values into XYZ
        double r = r1 / 255.0;
        double g = g1 / 255.0;
        double b = b1 / 255.0;

        if (r > 0.04045) {
            r = Math.pow(((r + 0.055) / 1.055), 2.4);
        } else {
            r = r / 12.92;
        }

        if (g > 0.04045) {
            g = Math.pow(((g + 0.055) / 1.055), 2.4);
        } else {
            g = g / 12.92;
        }

        if (b > 0.04045) {
            b = Math.pow(((b + 0.055) / 1.055), 2.4);
        } else {
            b = b / 12.92;
        }

        r = r * 100.0;
        g = g * 100.0;
        b = b * 100.0;

        // Observer. = 2°, Illuminant = D65
        double x = r * 0.4124 + g * 0.3576 + b * 0.1805;
        double y = r * 0.2126 + g * 0.7152 + b * 0.0722;
        double z = r * 0.0193 + g * 0.1192 + b * 0.9505;

        x = x / 95.047;   //Observer= 2°, Illuminant= D65
        y = y / 100.000;
        z = z / 108.883;

        if (x > 0.008856) {
            x = Math.pow(x, (1.0 / 3.0));
        } else {
            x = (7.787 * x) + (16.0 / 116.0);
        }

        if (y > 0.008856) {
            y = Math.pow(y, (1.0 / 3.0));
        } else {
            y = (7.787 * y) + (16.0 / 116.0);
        }

        if (z > 0.008856) {
            z = Math.pow(z, (1.0 / 3.0));
        } else {
            z = (7.787 * z) + (16.0 / 116.0);
        }

        double l1 = (116.0 * y) - 16.0;
        double a1 = 500.0 * (x - y);
        b1 = 200.0 * (y - z);

        r = r2 / 255.0;
        g = g2 / 255.0;
        b = b2 / 255.0;

        if (r > 0.04045) {
            r = Math.pow(((r + 0.055) / 1.055), 2.4);
        } else {
            r = r / 12.92;
        }

        if (g > 0.04045) {
            g = Math.pow(((g + 0.055) / 1.055), 2.4);
        } else {
            g = g / 12.92;
        }

        if (b > 0.04045) {
            b = Math.pow(((b + 0.055) / 1.055), 2.4);
        } else {
            b = b / 12.92;
        }

        r = r * 100.0;
        g = g * 100.0;
        b = b * 100.0;

        //Observer. = 2°, Illuminant = D65
        x = r * 0.4124 + g * 0.3576 + b * 0.1805;
        y = r * 0.2126 + g * 0.7152 + b * 0.0722;
        z = r * 0.0193 + g * 0.1192 + b * 0.9505;

        x = x / 95.047;   //Observer= 2°, Illuminant= D65
        y = y / 100.000;
        z = z / 108.883;

        if (x > 0.008856) {
            x = Math.pow(x, (1.0 / 3.0));
        } else {
            x = (7.787 * x) + (16.0 / 116.0);
        }

        if (y > 0.008856) {
            y = Math.pow(y, (1.0 / 3.0));
        } else {
            y = (7.787 * y) + (16.0 / 116.0);
        }

        if (z > 0.008856) {
            z = Math.pow(z, (1.0 / 3.0));
        } else {
            z = (7.787 * z) + (16.0 / 116.0);
        }

        //	Converts XYZ to Lab...
        double l2 = (116.0 * y) - 16.0;
        double a2 = 500.0 * (x - y);
        b2 = 200.0 * (y - z);

        // ...and then calculates distance between Lab colors, using the CIEDE2000 formula.
        double dl = l2 - l1;
        double hl = l1 + dl * 0.5;
        double sqb1 = b1 * b1;
        double sqb2 = b2 * b2;
        double c1 = Math.sqrt(a1 * a1 + sqb1);
        double c2 = Math.sqrt(a2 * a2 + sqb2);
        double hc7 = Math.pow(((c1 + c2) * 0.5), 7.0);
        double trc = Math.sqrt(hc7 / (hc7 + 6103515625.0));
        double t2 = 1.5 - trc * 0.5;
        double ap1 = a1 * t2;
        double ap2 = a2 * t2;
        c1 = Math.sqrt(ap1 * ap1 + sqb1);
        c2 = Math.sqrt(ap2 * ap2 + sqb2);
        double dc = c2 - c1;
        double hc = c1 + dc * 0.5;
        hc7 = Math.pow(hc, 7.0);
        trc = Math.sqrt(hc7 / (hc7 + 6103515625.0));
        double h1 = Math.atan2(b1, ap1);

        if (h1 < 0) {
            h1 = h1 + Math.PI * 2.0;
        }
        double h2 = Math.atan2(b2, ap2);

        if (h2 < 0) {
            h2 = h2 + Math.PI * 2.0;
        }

        double hdiff = h2 - h1;
        double hh = h1 + h2;
        if (Math.abs(hdiff) > Math.PI) {
            hh = hh + Math.PI * 2;
            if (h2 <= h1) {
                hdiff = hdiff + Math.PI * 2.0;
            }

        } else {
            hdiff = hdiff - Math.PI * 2.0;
        }

        hh = hh * 0.5;
        t2 = 1.0 - 0.17 * Math.cos(hh - Math.PI / 6.0) + 0.24 * Math.cos(hh * 2.0);
        t2 = t2 + 0.32 * Math.cos(hh * 3.0 + Math.PI / 30.0);
        t2 = t2 - 0.2 * Math.cos(hh * 4.0 - Math.PI * 63.0 / 180.0);
        double dh = 2.0 * Math.sqrt(c1 * c2) * Math.sin(hdiff * 0.5);
        double sqhl = (hl - 50.0) * (hl - 50.0);
        double fl = dl / (1.0 + (0.015 * sqhl / Math.sqrt(20.0 + sqhl)));
        double fc = dc / (hc * 0.045 + 1.0);
        double fh = dh / (t2 * hc * 0.015 + 1.0);
        double dt = 30 * Math.exp(-(Math.pow(36.0 * hh - 55.0 * Math.PI, 2.0)) / (25.0 * Math.PI * Math.PI));
        r = 0 - 2.0 * trc * Math.sin(2.0 * dt * Math.PI / 180.0);

        return Math.sqrt(fl * fl + fc * fc + fh * fh + r * fc * fh);
    }

    public static BufferedImage convertImage(BufferedImage input, int palette[][]) throws Exception {
        int w = input.getWidth();
        int h = input.getHeight();
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        convertImageInternal(input, output, palette);
        return output;
    }

    public static void convertImageInternal(BufferedImage input, BufferedImage output, int palette[][]) {
        int IMAGE_WIDTH = input.getWidth();
        int IMAGE_HEIGHT = input.getHeight();
        int luminosity[][] = new int[IMAGE_WIDTH][IMAGE_HEIGHT];
        double detail[][] = new double[IMAGE_WIDTH][IMAGE_HEIGHT];

        int octetr[] = new int[8];
        int octetg[] = new int[8];
        int octetb[] = new int[8];
        double octetdetail[] = new double[8];
        int octetfinal[] = new int[8];
        int octetvalue[] = new int[8];

        int toner[] = new int[3];
        int toneg[] = new int[3];
        int toneb[] = new int[3];
        double distcolor[] = new double[3];

        // Reads all luminosity values
        for (int j = 0; j < IMAGE_HEIGHT; j++) {
            for (int i = 0; i < IMAGE_WIDTH; i++) {
                int rgba[] = getRGBA(input, i, j);
                int r = rgba[0], g = rgba[1], b = rgba[2];
                luminosity[i][j] = (r + g + b) / 3;
            }
        }

        // Calculate the level of detail:
        if (detaillevel < IMAGE_WIDTH - 1) {
            for (int j = 1; j < IMAGE_HEIGHT - 1; j++) {
                for (int i = 1; i < IMAGE_WIDTH - 1; i++) {
                    int cor = luminosity[i - 1][j];
                    int cor2 = luminosity[i][j];
                    int cor3 = luminosity[i + 1][j];
                    int dif1 = Math.abs(cor - cor2);
                    int dif2 = Math.abs(cor2 - cor3);
                    int corfinal = dif2;
                    if (dif1 > dif2) {
                        corfinal = dif1;
                    }

                    cor = luminosity[i][j - 1];
                    cor3 = luminosity[i][j + 1];
                    dif1 = Math.abs(cor - cor2);
                    dif2 = Math.abs(cor2 - cor3);
                    int corfinal2 = dif2;
                    if (dif1 > dif2) {
                        corfinal2 = dif1;
                    }

                    corfinal = (corfinal + corfinal2) >> 1; // Shr 1
                    detail[i][j] = corfinal;
                }
            }

            for (int i = 0; i < IMAGE_WIDTH; i++) {
                detail[i][0] = 0;
                detail[i][IMAGE_HEIGHT - 1] = 0;
            }
            for (int i = 0; i < IMAGE_HEIGHT; i++) {
                detail[0][i] = 0;
                detail[IMAGE_WIDTH - 1][i] = 0;
            }

            for (int j = 0; j < IMAGE_HEIGHT; j++) {
                for (int i = 0; i < IMAGE_WIDTH; i++) {
                    if (detail[i][j] < 1) {
                        detail[i][j] = 1;
                    }
                    detail[i][j] = (detail[i][j] / detaillevel) + 1;
                }
            }
        } else {
            for (int j = 0; j < IMAGE_HEIGHT; j++) {
                for (int i = 0; i < IMAGE_WIDTH; i++) {
                    detail[i][j] = 1;
                }
            }
        }

        int x = 0, y = 0;
        while (y < IMAGE_HEIGHT) {
            int bestcor1 = 0, bestcor2 = 0;
            double bestdistance = Double.MAX_VALUE;

            for (int i = 0; i < 8; i++) {
                // Get the RGB values of 8 pixels of the original image
                int rgba[] = getRGBA(input, ORG_X + x + i, ORG_Y + y);
                int r = rgba[0], g = rgba[1], b = rgba[2];
                octetr[i] = r;
                octetg[i] = g;
                octetb[i] = b;
                octetdetail[i] = detail[x + i][y];
            }

            // Brute force starts. Programs tests all 15 x 15 MSX color combinations. For each pixel octet it'll have
            // to compare the original pixel colors with three different colors:
            // two MSX colors and a mixed RGB of both. If this RGB mixed is chosen it'll later be substituted by dithering.
            for (int cor1 = 1; cor1 <= N_COLORS; cor1++) {
                for (int cor2 = cor1; cor2 <= N_COLORS; cor2++) {

                    // If KeyHit(1) Then End
                    // First MSX color of the octet
                    toner[0] = palette[cor1][0];
                    toneg[0] = palette[cor1][1];
                    toneb[0] = palette[cor1][2];

                    // A mix of both MSX colors RGB values. Since MSX cannot mix colors,
                    // later if this color is chosen it'll be substituted by a 2x2 dithering pattern.
                    toner[1] = (palette[cor1][0] + palette[cor2][0]) / 2;
                    toneg[1] = (palette[cor1][1] + palette[cor2][1]) / 2;
                    toneb[1] = (palette[cor1][2] + palette[cor2][2]) / 2;

                    // Second MSX color of the octet
                    toner[2] = palette[cor2][0];
                    toneg[2] = palette[cor2][1];
                    toneb[2] = palette[cor2][2];

                    // if colors are not too distant according to the tolerance parameter, octect will be dithered.
                    if (DITHERING
                            && calcdist2000(toner[0], toneg[0], toneb[0],
                                    toner[2], toneg[2], toneb[2]) <= tolerance) {
                        // dithered
                        double dist = 0;
                        for (int i = 0; i < 8; i++) {
                            for (int j = 0; j < 3; j++) {
                                distcolor[j] = (calcdist2000(toner[j], toneg[j], toneb[j], octetr[i], octetg[i], octetb[i])) * octetdetail[i];
                            }
                            double finaldist = distcolor[0];
                            octetvalue[i] = 0;
                            for (int j = 1; j <= 2; j++) {
                                if (distcolor[j] < finaldist) {
                                    finaldist = distcolor[j];
                                    octetvalue[i] = j;
                                }
                            }

                            dist = dist + finaldist;
                            if (dist > bestdistance) {
                                break;
                            }
                        }
                        if (dist < bestdistance) {
                            bestdistance = dist;
                            bestcor1 = cor1;
                            bestcor2 = cor2;
                            for (int k = 0; k < 8; k++) {
                                octetfinal[k] = octetvalue[k];
                            }
                        }
                    } else {
                        // not dithered
                        double dist = 0;
                        for (int i = 0; i <= 7; i++) {
                            double finaldista = (calcdist2000(toner[0], toneg[0], toneb[0], octetr[i], octetg[i], octetb[i])) * octetdetail[i];
                            double finaldistb = (calcdist2000(toner[2], toneg[2], toneb[2], octetr[i], octetg[i], octetb[i])) * octetdetail[i];
                            double finaldist;

                            if (finaldista < finaldistb) {
                                octetvalue[i] = 0;
                                finaldist = finaldista;
                            } else {
                                octetvalue[i] = 2;
                                finaldist = finaldistb;
                            }
                            dist = dist + finaldist;
                            if (dist > bestdistance) {
                                break;
                            }
                        }
                        if (dist < bestdistance) {
                            bestdistance = dist;
                            bestcor1 = cor1;
                            bestcor2 = cor2;
                            for (int k = 0; k < 8; k++) {
                                octetfinal[k] = octetvalue[k];
                            }
                        }
                    }

                    if (bestdistance == 0) {
                        break;
                    }
                }

                if (bestdistance == 0) {
                    break;
                }
            }

            for (int i = 0; i < 8; i++) {
                int r = 0, g = 0, b = 0, a = 255;
                switch (octetfinal[i]) {
                    case 0:
                        r = palette[bestcor1][0];
                        g = palette[bestcor1][1];
                        b = palette[bestcor1][2];
                        break;
                    case 1:
                        if (y % 2 == i % 2) {
                            r = palette[bestcor2][0];
                            g = palette[bestcor2][1];
                            b = palette[bestcor2][2];
                        } else {
                            r = palette[bestcor1][0];
                            g = palette[bestcor1][1];
                            b = palette[bestcor1][2];
                        }
                        break;

                    case 2:
                        r = palette[bestcor2][0];
                        g = palette[bestcor2][1];
                        b = palette[bestcor2][2];
                        break;
                }

                output.setRGB(x + i, y, (a << 24) + (r << 16) + (g << 8) + b);
            }
            y++;
            if ((y % 8) == 0) {
                y -= 8;
                x += 8;
            }
            if (x >= IMAGE_WIDTH) {
                x = 0;
                y += 8;
            }
        }
    }

}
