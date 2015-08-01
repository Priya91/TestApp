README.md
=========

Copy ```media/video.mp4``` and ```media/audio.m4a``` to emulator:

```
adb push video.mp4 /sdcard/reversedub/video.mp4
adb push audio.m4a /sdcard/reversedub/audio.m4a
```

The output of the method is dropped at ```/sdcard/reversedub/output.mp4```

This can be copied to PC by:
```
adb pull /sdcard/reversedub/output.mp4 media/output.mp4
```
