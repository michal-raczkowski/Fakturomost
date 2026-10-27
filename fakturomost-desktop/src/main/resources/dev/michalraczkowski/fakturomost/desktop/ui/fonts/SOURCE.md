# Bundled font

IBM Plex Sans, weights Regular, SemiBold and Bold, unmodified TTF files from
[IBM/plex](https://github.com/IBM/plex), tag `@ibm/plex-sans@1.1.0`,
`packages/plex-sans/fonts/complete/ttf/`.

Licence: SIL Open Font License 1.1 (`OFL-IBM-Plex.txt`, copied from the same tag). "Plex" is a
Reserved Font Name, so the files must stay unmodified (no subsetting or renaming).

SHA-256:

```
975dcda37d80f038dcd143c22e33ca2d97a0cc5a929aace1c749153b0fe1afa5  IBMPlexSans-Regular.ttf
a20caf8286023a6a7a85e40b1d2a4ae9fc3e3b1f9eda8f4c542dd4986af67bb1  IBMPlexSans-SemiBold.ttf
9e6c74a889a700d707613d24548fe4ffa6bc59559a0689d2cf9e133bdcdafb2f  IBMPlexSans-Bold.ttf
```

Why this font: the UI must look the same on Windows, macOS and Linux, and the system UI fonts
differ on each. Plex Sans covers Polish, and its digits are tabular by default (every digit is 600
units wide), so amount columns line up — JavaFX cannot switch on the OpenType `tnum` feature.
