# Synthetic media fixtures

These fixtures contain only generated test signals and patterns; no copyrighted source media is included.

Generated with FFmpeg 8.0:

```powershell
ffmpeg -f lavfi -i "sine=frequency=440:duration=2" -c:a libmp3lame -b:a 64k synthetic-tone.mp3
ffmpeg -f lavfi -i "sine=frequency=440:duration=2" -c:a aac -b:a 64k synthetic-tone.m4a
ffmpeg -f lavfi -i "testsrc2=s=16x16:d=1:r=2" -c:v libwebp -loop 0 synthetic-extended.webp
```

The WebP fixture is animated and therefore exercises the extended `VP8X`/`ANIM`/`ANMF` container.
