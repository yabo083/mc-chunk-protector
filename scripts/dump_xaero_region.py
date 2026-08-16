#!/usr/bin/env python3
"""Dump the binary header of a Xaero world-map region.xaero to understand its format."""
import zipfile, struct, sys, os

path = r"E:\SteamLibrary\steamapps\common\PCL2\.minecraft\versions\Mechanomania\xaero\world-map\Multiplayer_niumaclub.top\null\mw$-540754784\-1_-1.zip"

with zipfile.ZipFile(path) as z:
    print("== entries ==")
    for i in z.infolist():
        print(f"  {i.filename} size={i.file_size} comp={i.compress_size}")
    name = z.namelist()[0]
    data = z.read(name)
    print(f"== {name} raw bytes: {len(data)} ==")
    print("first 128 bytes:")
    print(data[:128].hex(' '))
    # heuristic: try to identify file type
    import re
    ascii_prefix = data[:32].decode('latin1')
    print("ascii prefix repr:", repr(ascii_prefix))
