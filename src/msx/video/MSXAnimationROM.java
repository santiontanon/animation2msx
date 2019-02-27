/*
 * Santiago Ontanon Villar.
 */
package msx.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.List;
import msx.tools.MSXTile;
import msx.tools.Pletter;

/**
 *
 * @author santi
 * 
 * This class encapsulates functions to generate an MSX ROM that demoes how to play
 * the animation that has been converted to MSX with the "ConvertVideo" class.
 * 
 */
public class MSXAnimationROM {

    static void generateAssemblerFiles(List<int[]> nameTables, List<List<MSXTile>> clusters, ConvertVideo.Configuration config) throws Exception
    {
        // Save the tile files:
        for(int bank = 0;bank<3;bank++) {
            List<MSXTile> cluster = clusters.get(bank);
            if (cluster.isEmpty()) continue;
            FileWriter fw = new FileWriter(config.outputFolder + "tiles-bank"+(bank+1)+".asm");
            fw.write("    org #0000\n");
            fw.write("patterns:\n");
            for(MSXTile t:cluster) {
                int bytes[] = t.patternBytes();
                fw.write("    db ");
                for(int i = 0;i<8;i++) {
                    if (i==0) {
                        fw.write("" + bytes[i]);
                    } else {
                        fw.write(", " + bytes[i]);
                    }
                }
                fw.write("\n");
            }
            if (config.uniformAttributes == null) {
                fw.write("attributes:\n");
                int lastAttribute = 0;
                for(MSXTile t:cluster) {
                    int bytes[] = t.patternBytes();
                    fw.write("    db ");
                    for(int i = 0;i<8;i++) {
                        int b = bytes[i+8];
                        if (b == 0 && (lastAttribute & 0x0f)==0) {
                            b = lastAttribute;
                        }
                        if (i==0) {
                            fw.write("" + b);
                        } else {
                            fw.write(", " + b);
                        }
                        lastAttribute = b;
                    }
                    fw.write("\n");
                }
            }
            fw.flush();
            fw.close();
        }
        
        // Save the nameTables:
        {
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
            
            FileWriter fw = new FileWriter(config.outputFolder + "nametables.asm");
            fw.write("    org #0000\n");
            for(int frame = 0;frame<nameTables.size();frame++) {
                fw.write("frame" + (frame+1)+":\n");
                for(int i = 0;i<height;i++) {
                    fw.write("    db ");
                    for(int j = 0;j<width;j++) {
                        if (j==0) {
                            fw.write(""+(nameTables.get(frame)[i*width+j]+config.startingTile));
                        } else {
                            fw.write(", "+(nameTables.get(frame)[i*width+j]+config.startingTile));
                        }
                    }
                    fw.write("\n");
                }
            }            
            fw.flush();
            fw.close();
        }
        
    }

    
    static boolean generateROM(List<int[]> nameTables, List<List<MSXTile>> clusters, ConvertVideo.Configuration config) throws Exception
    {
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

        // 1) Compile the data with Glass and compress with Pletter:
        for(int bank = 1;bank<=3;bank++) {
            List<MSXTile> cluster = clusters.get(bank-1);
            if (cluster.isEmpty()) continue;
            String command = "java -jar lib"+File.separator+"glass-0.5.jar " + 
                             config.outputFolder + "tiles-bank"+bank+".asm " + 
                             config.outputFolder + "tiles-bank"+bank+".bin";
            if (!runSystemCommand(command)) return false;
            Pletter.main(new String[]{config.outputFolder + "tiles-bank"+bank+".bin",
                                      config.outputFolder + "tiles-bank"+bank+".plt"});
        }
        String command = "java -jar lib"+File.separator+"glass-0.5.jar " + 
                         config.outputFolder + "nametables.asm " + 
                         config.outputFolder + "nametables.bin";
        if (!runSystemCommand(command)) return false;
        Pletter.main(new String[]{config.outputFolder + "nametables.bin",
                                  config.outputFolder + "nametables.plt"});
        
        // 2) Generate a main.asm file that runs the animation:
        String assemblerMainFileContent = 
                ";-----------------------------------------------\n" +
                "STARTTILE: equ "+config.startingTile+"\n";
        if (!clusters.get(0).isEmpty()) {
            assemblerMainFileContent +=
                    "N_TILES_BANK1:  equ "+clusters.get(0).size()+"\n";
        }
        if (!clusters.get(1).isEmpty()) {
            assemblerMainFileContent +=
                    "N_TILES_BANK2:  equ "+clusters.get(1).size()+"\n";
        }
        if (!clusters.get(2).isEmpty()) {
            assemblerMainFileContent +=
                    "N_TILES_BANK3:  equ "+clusters.get(2).size()+"\n";
        }
        assemblerMainFileContent +=
                "N_ANIMATION_FRAMES: equ "+nameTables.size()+"\n" +
                "\n" +
                "VDP.DW: equ #0007\n" +
                "\n" +
                "CLIKSW: equ #f3db       ; keyboard sound\n" +
                "VDP_REGISTER_1: equ #f3e0\n" +
                "BAKCLR: equ #f3ea\n" +
                "BDRCLR: equ #f3eb\n" +
                "TIMI:   equ #fd9f       ; timer interrupt hook\n" +
                "HKEY:   equ #fd9a       ; hkey interrupt hook\n" +
                "\n" +
                "SETWRT: equ #0053\n" +
                "FILVRM: equ #0056\n" +
                "CHGMOD: equ #005f\n" +
                "CHGCLR: equ #0062\n" +
                "\n" +
                "CHRTBL2: equ #0000   ; pattern table address\n" +
                "NAMTBL2: equ #1800   ; name table address \n" +
                "CLRTBL2: equ #2000   ; color table address             \n" +
                "\n" +
                "\n" +
                "    org #4000\n" +
                ";-----------------------------------------------\n" +
                "    db \"AB\"     ; ROM signature\n" +
                "    dw Execute  ; start address\n" +
                "    db 0,0,0,0,0,0,0,0,0,0,0,0\n" +
                "\n" +
                "\n" +
                ";-----------------------------------------------\n" +
                "; Code that gets executed when the game starts\n" +
                "Execute:\n" +
                "    di\n" +
                "    ; init the stack:\n" +
                "    ld sp,#F380\n" +
                "    ; reset some interrupts to make sure it runs in some MSX computers \n" +
                "    ; with disk controllers installed in some interrupt handlers\n" +
                "    ld a,#C9\n" +
                "    ld (HKEY),a\n" +
                "    ld (TIMI),a\n" +
                "    ei\n" +
                "\n" +
                "    ; Silence, init keyboard, and clear config:\n" +
                "    xor a\n" +
                "    ld (CLIKSW),a\n" +
                "    ; Change background colors:\n" +
                "    ld (BAKCLR),a\n" +
                "    ld (BDRCLR),a\n" +
                "    call CHGCLR\n" +
                "   \n" +
                "    ld a,2      ; Change screen mode\n" +
                "    call CHGMOD\n" +
                "\n" +
                "    call disable_VDP_output\n" +
                "    call loadAnimationTilesToVDP\n" +
                "    ld hl,animationNameTables_pletter\n" +
                "    ld de,animationNameTables\n" +
                "    call pletter_unpack\n" +
                "    xor a\n" +
                "    call drawAnimationFrame\n" +
                "    call enable_VDP_output\n" +
                "\n" +
                "animationRestart:\n" +
                "    xor a\n" +
                "animationLoop:\n" +
                "    push af\n" +
                "    call drawAnimationFrame\n" +
                "    pop af\n" +
                "    \n";
        for(int i = 0;i<config.halts;i++) {
            assemblerMainFileContent += "    halt\n";
        }
        assemblerMainFileContent += 
                "    inc a\n" +
                "    cp N_ANIMATION_FRAMES\n" +
                "    jr nz,animationLoop\n" +
                "    jr animationRestart\n" +
                "\n" +
                "\n" +
                ";-----------------------------------------------\n" +
                "loadAnimationTilesToVDP:\n";
        if (!clusters.get(0).isEmpty()) {
            assemblerMainFileContent +=
                "    ld hl,tiles_bank1_pletter\n" +
                "    ld de,buffer\n" +
                "    call pletter_unpack\n" +
                "    ld hl,buffer\n" +
                "    ld de,CHRTBL2+STARTTILE*8\n" +
                "    ld bc,N_TILES_BANK1*8\n" +
                "    call fast_LDIRVM\n";
            if (config.uniformAttributes == null) {
                assemblerMainFileContent +=
                    "    ld hl,buffer+N_TILES_BANK1*8\n" +
                    "    ld de,CLRTBL2+STARTTILE*8\n" +
                    "    ld bc,N_TILES_BANK1*8\n" +
                    "    call fast_LDIRVM\n" +
                    "\n";
            } else {
                assemblerMainFileContent +=
                    "    ld a," + (config.uniformAttributes[0] + config.uniformAttributes[1]*16) + "\n" +
                    "    ld hl,CLRTBL2+STARTTILE*8\n" +
                    "    ld bc,N_TILES_BANK1*8\n" +
                    "    call FILVRM\n" +
                    "\n";
            }
        }
        if (!clusters.get(1).isEmpty()) {
            assemblerMainFileContent +=
                "    ld hl,tiles_bank2_pletter\n" +
                "    ld de,buffer\n" +
                "    call pletter_unpack\n" +
                "    ld hl,buffer\n" +
                "    ld de,CHRTBL2+(256+STARTTILE)*8\n" +
                "    ld bc,N_TILES_BANK2*8\n" +
                "    call fast_LDIRVM\n";
            if (config.uniformAttributes == null) {
                assemblerMainFileContent +=            
                    "    ld hl,buffer+N_TILES_BANK2*8\n" +
                    "    ld de,CLRTBL2+(256+STARTTILE)*8\n" +
                    "    ld bc,N_TILES_BANK2*8\n" +
                    "    call fast_LDIRVM\n" +
                    "\n";
            } else {
                assemblerMainFileContent +=
                    "    ld a," + (config.uniformAttributes[0] + config.uniformAttributes[1]*16) + "\n" +
                    "    ld hl,CLRTBL2+(256+STARTTILE)*8\n" +
                    "    ld bc,N_TILES_BANK2*8\n" +
                    "    call FILVRM\n" +
                    "\n";
            }
        }
        if (!clusters.get(2).isEmpty()) {
            assemblerMainFileContent +=
                "    ld hl,tiles_bank3_pletter\n" +
                "    ld de,buffer\n" +
                "    call pletter_unpack\n" +
                "    ld hl,buffer\n" +
                "    ld de,CHRTBL2+(512+STARTTILE)*8\n" +
                "    ld bc,N_TILES_BANK3*8\n" +
                "    call fast_LDIRVM\n";
            if (config.uniformAttributes == null) {
                assemblerMainFileContent +=            
                    "    ld hl,buffer+N_TILES_BANK3*8\n" +
                    "    ld de,CLRTBL2+(512+STARTTILE)*8\n" +
                    "    ld bc,N_TILES_BANK3*8\n" +
                    "    jp fast_LDIRVM\n" +
                    "\n";
            } else {
                assemblerMainFileContent +=
                    "    ld a," + (config.uniformAttributes[0] + config.uniformAttributes[1]*16) + "\n" +
                    "    ld hl,CLRTBL2+(512+STARTTILE)*8\n" +
                    "    ld bc,N_TILES_BANK3*8\n" +
                    "    call FILVRM\n" +
                    "\n";
            }
        }
        if (width == 32) {
            assemblerMainFileContent +=                        
                    "\n" +
                    ";-----------------------------------------------\n" +
                    "drawAnimationFrame:\n" +
                    "    ld hl,animationNameTables\n" +
                    "    ld bc,"+(width*height)+"\n" +
                    "    or a\n" +
                    "    jr z,drawAnimationFrame_ptr_done\n" +
                    "drawAnimationFrame_ptr_loop:\n" +
                    "    add hl,bc\n" +
                    "    dec a\n" +
                    "    jr nz,drawAnimationFrame_ptr_loop\n" +
                    "drawAnimationFrame_ptr_done:\n" +
                    "    ld de,NAMTBL2+"+(starty*32)+"\n" +
                    "    ld bc,"+(width*height)+"\n" +
                    "    jp fast_LDIRVM\n" +
                    "\n";
        } else {
            assemblerMainFileContent +=                        
                    "\n" +
                    ";-----------------------------------------------\n" +
                    "drawAnimationFrame:\n" +
                    "    ld hl,animationNameTables\n" +
                    "    ld bc,"+(width*height)+"\n" +
                    "    or a\n" +
                    "    jr z,drawAnimationFrame_ptr_done\n" +
                    "drawAnimationFrame_ptr_loop:\n" +
                    "    add hl,bc\n" +
                    "    dec a\n" +
                    "    jr nz,drawAnimationFrame_ptr_loop\n" +
                    "drawAnimationFrame_ptr_done:\n" +
                    "    ld b," + height + "\n" +
                    "    ld de,NAMTBL2+"+(starty*32+startx)+"\n" +
                    "drawAnimationFrame_loop:\n"+
                    "    push bc\n" +
                    "    push hl\n" +
                    "    push de\n" +
                    "    ld bc,"+width+"\n" +
                    "    call fast_LDIRVM\n" +
                    "    pop hl\n" +
                    "    ld bc,32\n" +
                    "    add hl,bc\n" +
                    "    ex de,hl\n" +
                    "    pop hl\n" +
                    "    ld bc,"+width+"\n" +
                    "    add hl,bc\n" +
                    "    pop bc\n" +
                    "    djnz drawAnimationFrame_loop\n" +
                    "    ret\n" +
                    "\n";
        }
        assemblerMainFileContent +=                        
                "\n" +
                ";-----------------------------------------------\n" +
                "disable_VDP_output:\n" +
                "    ld a,(VDP_REGISTER_1)\n" +
                "    and #bf ; reset the BL bit\n" +
                "    di\n" +
                "    out (#99),a\n" +
                "    ld  a,1+128 ; write to register 1\n" +
                "    ei\n" +
                "    out (#99),a\n" +
                "    ret\n" +
                "\n" +
                "\n" +
                ";-----------------------------------------------\n" +
                "enable_VDP_output:\n" +
                "    ld a,(VDP_REGISTER_1)\n" +
                "    or #40  ; set the BL bit\n" +
                "    di\n" +
                "    out (#99),a\n" +
                "    ld  a,1+128 ; write to register 1\n" +
                "    ei\n" +
                "    out (#99),a\n" +
                "    ret\n" +
                "\n" +
                "\n" +
                ";-----------------------------------------------\n" +
                "; hl: source data\n" +
                "; de: target address in the VDP\n" +
                "; bc: amount to copy\n" +
                "fast_LDIRVM:\n" +
                "    ex de,hl\n" +
                "    push de\n" +
                "    push bc\n" +
                "    call SETWRT\n" +
                "    pop bc\n" +
                "    pop hl\n" +
                "copy_to_VDP:\n" +
                "    ld e,b\n" +
                "    ld a,c\n" +
                "    or a\n" +
                "    jr z,copy_to_VDP_lsb_0\n" +
                "    inc e\n" +
                "copy_to_VDP_lsb_0:\n" +
                "    ld b,c\n" +
                "    ld a,(VDP.DW)\n" +
                "    ld c,a\n" +
                "    ld a,e\n" +
                "copy_to_VDP_loop2:\n" +
                "copy_to_VDP_loop:\n" +
                "    outi\n" +
                "    jp nz,copy_to_VDP_loop\n" +
                "    dec a\n" +
                "    jp nz,copy_to_VDP_loop2\n" +
                "    ret\n" +
                "\n" +
                "\n" +
                ";-----------------------------------------------\n" +
                "; pletter v0.5c msx unpacker\n" +
                "; call unpack with \"hl\" pointing to some pletter5 data, and \"de\" pointing to the destination.\n" +
                "; changes all registers\n" +
                "\n" +
                "GETBIT:  MACRO \n" +
                "    add a,a\n" +
                "    call z,pletter_getbit\n" +
                "    ENDM\n" +
                "\n" +
                "GETBITEXX:  MACRO \n" +
                "    add a,a\n" +
                "    call z,pletter_getbitexx\n" +
                "    ENDM\n" +
                "\n" +
                "pletter_unpack:\n" +
                "    ld a,(hl)\n" +
                "    inc hl\n" +
                "    exx\n" +
                "    ld de,0\n" +
                "    add a,a\n" +
                "    inc a\n" +
                "    rl e\n" +
                "    add a,a\n" +
                "    rl e\n" +
                "    add a,a\n" +
                "    rl e\n" +
                "    rl e\n" +
                "    ld hl,pletter_modes\n" +
                "    add hl,de\n" +
                "    ld e,(hl)\n" +
                "    ld ixl,e\n" +
                "    inc hl\n" +
                "    ld e,(hl)\n" +
                "    ld ixh,e\n" +
                "    ld e,1\n" +
                "    exx\n" +
                "    ld iy,pletter_loop\n" +
                "pletter_literal:\n" +
                "    ldi\n" +
                "pletter_loop:\n" +
                "    GETBIT\n" +
                "    jr nc,pletter_literal\n" +
                "    exx\n" +
                "    ld h,d\n" +
                "    ld l,e\n" +
                "pletter_getlen:\n" +
                "    GETBITEXX\n" +
                "    jr nc,pletter_lenok\n" +
                "pletter_lus:\n" +
                "    GETBITEXX\n" +
                "    adc hl,hl\n" +
                "    ret c\n" +
                "    GETBITEXX\n" +
                "    jr nc,pletter_lenok\n" +
                "    GETBITEXX\n" +
                "    adc hl,hl\n" +
                "    ret c\n" +
                "    GETBITEXX\n" +
                "    jp c,pletter_lus\n" +
                "pletter_lenok:\n" +
                "    inc hl\n" +
                "    exx\n" +
                "    ld c,(hl)\n" +
                "    inc hl\n" +
                "    ld b,0\n" +
                "    bit 7,c\n" +
                "    jr z,pletter_offsok\n" +
                "    jp ix\n" +
                "\n" +
                "pletter_mode6:\n" +
                "    GETBIT\n" +
                "    rl b\n" +
                "pletter_mode5:\n" +
                "    GETBIT\n" +
                "    rl b\n" +
                "pletter_mode4:\n" +
                "    GETBIT\n" +
                "    rl b\n" +
                "pletter_mode3:\n" +
                "    GETBIT\n" +
                "    rl b\n" +
                "pletter_mode2:\n" +
                "    GETBIT\n" +
                "    rl b\n" +
                "    GETBIT\n" +
                "    jr nc,pletter_offsok\n" +
                "    or a\n" +
                "    inc b\n" +
                "    res 7,c\n" +
                "pletter_offsok:\n" +
                "    inc bc\n" +
                "    push hl\n" +
                "    exx\n" +
                "    push hl\n" +
                "    exx\n" +
                "    ld l,e\n" +
                "    ld h,d\n" +
                "    sbc hl,bc\n" +
                "    pop bc\n" +
                "    ldir\n" +
                "    pop hl\n" +
                "    jp iy\n" +
                "\n" +
                "pletter_getbit:\n" +
                "    ld a,(hl)\n" +
                "    inc hl\n" +
                "    rla\n" +
                "    ret\n" +
                "\n" +
                "pletter_getbitexx:\n" +
                "    exx\n" +
                "    ld a,(hl)\n" +
                "    inc hl\n" +
                "    exx\n" +
                "    rla\n" +
                "    ret\n" +
                "\n" +
                "pletter_modes:\n" +
                "    dw pletter_offsok\n" +
                "    dw pletter_mode2\n" +
                "    dw pletter_mode3\n" +
                "    dw pletter_mode4\n" +
                "    dw pletter_mode5\n" +
                "    dw pletter_mode6\n" +
                "\n" +
                "\n" +
                "\n";
        if (!clusters.get(0).isEmpty()) {
            assemblerMainFileContent +=
                "tiles_bank1_pletter:\n" +
                "    incbin \"tiles-bank1.plt\"\n";
        }
        if (!clusters.get(1).isEmpty()) {
            assemblerMainFileContent +=
                "tiles_bank2_pletter:\n" +
                "    incbin \"tiles-bank2.plt\"\n";
        }
        if (!clusters.get(2).isEmpty()) {
            assemblerMainFileContent +=
                "tiles_bank3_pletter:\n" +
                "    incbin \"tiles-bank3.plt\"\n";
        }
        assemblerMainFileContent +=
                "animationNameTables_pletter:\n" +
                "    incbin \"nametables.plt\"\n" +
                "\n" +
                "endOfROM:\n" +
                "    ds ((($-1)/#4000)+1)*#4000-$\n" +
                "\n" +
                ";-----------------------------------------------\n" +
                "; RAM:\n" +
                "    org #c000   ; RAM goes to the 4th slot\n" +
                "buffer:\n" +
                "animationNameTables:\n";
        
        int idx1 = config.inputGifFileName.lastIndexOf(File.separator);
        int idx2 = config.inputGifFileName.lastIndexOf(".");
        if (idx1 <= 0) idx1 = 0;
        if (idx2 <= 9) idx2 = config.inputGifFileName.length();
        String outputName = config.inputGifFileName.substring(idx1, idx2);
        
        FileWriter mainfw = new FileWriter(config.outputFolder + outputName + ".asm");
        mainfw.write(assemblerMainFileContent);
        mainfw.flush();
        mainfw.close();
        
        // 3) Compile the whole thing together:
        command = "java -jar lib"+File.separator+"glass-0.5.jar " + 
                         config.outputFolder + outputName + ".asm " + 
                         config.outputFolder + outputName + ".rom";
        if (!runSystemCommand(command)) return false;
        
        return true;
    }
    
    
    static boolean runSystemCommand(String command)
    {
        try {
            String s;
            Process p = Runtime.getRuntime().exec(command);            
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((s = stdInput.readLine()) != null) System.out.println(s);
            while ((s = stdError.readLine()) != null) System.out.println(s);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
}
