# animation2msx

A small Java tool to convert short animation loops to be played on MSX computers. This is the code that was used to generate the flag animation in the game XRacing ( https://github.com/santiontanon/xracing ).

The input is a GIF file, and the output is a collection of .asm file with the data necessary to play the animation. The data is divded into 2 parts:
- Tiles/attributes: to be copied to each of the three banks of the VDP (the converted assumes MSX1 with Screen 2).
- Name tables: the name tables of each of the animation frames, to be copied to the VDP at each frame. 

Additionally, just to see the animation in an MSX. The tool prepares a small .asm file to show how to play the animation, and compiles it into a ROM that can directly be open on an MSX emulator.

The way the program works is by figuring out all the different tiles needed in each of the 3 banks to play the animation. Since only 256 tiles can be loaded at once, it assumes that only the name table can change from frame to frame, and it uses an automated clustering algorithm (k-medoids) to reduce the number of tiles to 256 (or to a smaller number if specified), in order to get an approximation of the animation.

Note: color conversion is not very good in the tool, so, I recommend processing the GIF file manually to get it into an MSX-style palette first.

Note 2: if the animation is complex and there are lots of tiles, the execution of k-medoids can be slow! For example, it takes about 15 minutes to complete in my laptop for the cube1.gif example. Flag.gif just takes a couple of seconds, however, since its a much simpler animation.

# limitations

- Assumes MSX screen 2 (256x192 pixels, 16 colors)
- Color conversion is very simple (nearest neighbor to the MSX1 color palette). If you want to do better color conversion, adjust the color of the input GIF first with your favorite image editing tool, and then pass it through this tool to convert it to MSX
- The demo ROM will break if the animation name tables use more than about 12KB (this is enough for about 16 frames if the video is full screen, and more if the video is smaller).

# usage as a command line tool:

Arguments:
- input gif file (e.g. examples/flag.gif)
- (optional) output folder (e.g. documents/my-msx-flag). If this is not specified, all output files will be generated in the same folder as the input gif file.

Options:
- -p filename: to specify a different MSX palette than the default one. See the examples folder for an example of how to specify a palette in a csv/tsv file (one color per row with 3 columns per row with the RGB values in the 0 - 255 range, separated by spaces, commas or tabs).
- -r "tile range to use": by default, all 256 (tiles from 0 to 255) tiles in each of the 3 banks will be used. However, if a smaller range is desired, this can be specified with the -tr option. For example -tr 32-127 would force the program to only use tiles from 32-127 (96 tiles).
- -d "delay": the number of halt instructions between animation frames in the generated rom file. The default is 2
- -s "stride": by default, all the frames in the gif file will be used. However, some gif files are long. So, you can specify if you want to skip frames. By default, the stride is set to 1 (use every frame), but you can set it to 2 (skip every other frame), 3, etc. Also, notice that if the video has more than 16 frames, the demo ROM will not work, since the nameTables will not fit in memory.
- -nw: ignore the number of times each tile appears in the video during clustering.
- -sa x1,y1,x2,y2: source area to capture from the gif (default is the whole screen.
- -ta x1,y1,x2,y2: target area of the MSX screen (default is 0,0,256,192). Notice that if you specify a smaller area, the video demo ROM might display grabage in the rest of the screen.
- -c c1,c2: generate the animation using only two colors (c1,c2) for the whole animation. This saves space in the ROM, since we do not need to store the attributes table of the tiles. For example, to generate an animation in black and white, use -ua 0,15

Examples:
- java -jar MSXVideoConverter.jar -cp lib/glass-0.5.jar examples/flag.gif examples/flag
- java -jar MSXVideoConverter.jar -cp lib/glass-0.5.jar examples/flag.gif examples/flag -p examples/msxpalette.tsv
- java -jar MSXVideoConverter.jar -cp lib/glass-0.5.jar examples/flag.gif examples/flag -d 2 -r 112-255 -sa 0,0,256,184 -ta 0,0,256,184 -c 0,15   <-- this is to generate the flag animation exactly as it is in XRacing (the original XRacing used a slightly worse version of this algorithm (with the -nw option set).

