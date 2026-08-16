"""Extract spawn coords from a Minecraft level.dat (gzip compressed NBT)."""
import gzip, struct, sys

def read_nbt(fp, depth=0):
    tag_type = fp.read(1)[0]
    if tag_type == 0:  # TAG_End
        return ('end', None)
    name_len = struct.unpack('>H', fp.read(2))[0]
    name = fp.read(name_len).decode('utf-8', 'replace')
    if tag_type == 1:  # byte
        val = struct.unpack('>b', fp.read(1))[0]
    elif tag_type == 2:  # short
        val = struct.unpack('>h', fp.read(2))[0]
    elif tag_type == 3:  # int
        val = struct.unpack('>i', fp.read(4))[0]
    elif tag_type == 4:  # long
        val = struct.unpack('>q', fp.read(8))[0]
    elif tag_type == 5:  # float
        val = struct.unpack('>f', fp.read(4))[0]
    elif tag_type == 6:  # double
        val = struct.unpack('>d', fp.read(8))[0]
    elif tag_type == 7:  # byte array
        n = struct.unpack('>i', fp.read(4))[0]
        val = fp.read(n)
    elif tag_type == 8:  # string
        n = struct.unpack('>H', fp.read(2))[0]
        val = fp.read(n).decode('utf-8', 'replace')
    elif tag_type == 9:  # list
        elem_type = fp.read(1)[0]
        n = struct.unpack('>i', fp.read(4))[0]
        vals = []
        for _ in range(n):
            vals.append(read_nbt_elem(fp, elem_type))
        val = vals
    elif tag_type == 10:  # compound
        val = {}
        while True:
            sub = read_nbt(fp, depth+1)
            if sub[0] == 'end':
                break
            val[sub[1]] = sub[2]
    elif tag_type == 11:  # int array
        n = struct.unpack('>i', fp.read(4))[0]
        val = struct.unpack('>'+'i'*n, fp.read(4*n))
    elif tag_type == 12:  # long array
        n = struct.unpack('>i', fp.read(4))[0]
        val = struct.unpack('>'+'q'*n, fp.read(8*n))
    else:
        raise ValueError('unknown tag ' + str(tag_type))
    return (tag_type, name, val)

def read_nbt_elem(fp, elem_type):
    # element of a list: reuse compound/int/string readers
    if elem_type == 0:
        fp.read(1); return None
    name_len_hack = fp.read(1)  # not actually name; real formatting
    # For simplicity parse standard types without name
    return _read_elem(fp, elem_type)

def _read_elem(fp, t):
    if t == 1: return struct.unpack('>b', fp.read(1))[0]
    if t == 2: return struct.unpack('>h', fp.read(2))[0]
    if t == 3: return struct.unpack('>i', fp.read(4))[0]
    if t == 4: return struct.unpack('>q', fp.read(8))[0]
    if t == 5: return struct.unpack('>f', fp.read(4))[0]
    if t == 6: return struct.unpack('>d', fp.read(8))[0]
    if t == 7:
        n = struct.unpack('>i', fp.read(4))[0]; return fp.read(n)
    if t == 8:
        n = struct.unpack('>H', fp.read(2))[0]; return fp.read(n).decode('utf-8','replace')
    if t == 9:
        ft = fp.read(1)[0]; n = struct.unpack('>i', fp.read(4))[0]
        return [_read_elem(fp, ft) for _ in range(n)]
    if t == 10:
        # compound in list: tag/name/val repeated
        val = {}
        while True:
            tag_type = fp.read(1)[0]
            if tag_type == 0:
                break
            name_len = struct.unpack('>H', fp.read(2))[0]
            name = fp.read(name_len).decode('utf-8','replace')
            # reparse as element
            # easiest: seek back impossible; use dedicated compound elem
            entry = _read_compound_elem(fp, tag_type, name)
            val[entry[0]] = entry[1]
        return val
    if t == 11:
        n = struct.unpack('>i', fp.read(4))[0]
        return struct.unpack('>'+'i'*n, fp.read(4*n))
    if t == 12:
        n = struct.unpack('>i', fp.read(4))[0]
        return struct.unpack('>'+'q'*n, fp.read(8*n))
    return None

def _read_compound_elem(fp, tag_type, name):
    t = tag_type
    if t == 1: v = struct.unpack('>b', fp.read(1))[0]
    elif t == 2: v = struct.unpack('>h', fp.read(2))[0]
    elif t == 3: v = struct.unpack('>i', fp.read(4))[0]
    elif t == 4: v = struct.unpack('>q', fp.read(8))[0]
    elif t == 5: v = struct.unpack('>f', fp.read(4))[0]
    elif t == 6: v = struct.unpack('>d', fp.read(8))[0]
    elif t == 7:
        n = struct.unpack('>i', fp.read(4))[0]; v = fp.read(n)
    elif t == 8:
        n = struct.unpack('>H', fp.read(2))[0]; v = fp.read(n).decode('utf-8','replace')
    elif t == 9:
        ft = fp.read(1)[0]; n = struct.unpack('>i', fp.read(4))[0]
        v = [_read_elem(fp, ft) for _ in range(n)]
    elif t == 10:
        v = {}
        while True:
            tt2 = fp.read(1)[0]
            if tt2 == 0: break
            nl2 = struct.unpack('>H', fp.read(2))[0]
            nm2 = fp.read(nl2).decode('utf-8','replace')
            e = _read_compound_elem(fp, tt2, nm2)
            v[e[0]] = e[1]
    elif t == 11:
        n = struct.unpack('>i', fp.read(4))[0]; v = struct.unpack('>'+'i'*n, fp.read(4*n))
    elif t == 12:
        n = struct.unpack('>i', fp.read(4))[0]; v = struct.unpack('>'+'q'*n, fp.read(8*n))
    else:
        v = None
    return (name, v)

path = r'E:\SteamLibrary\steamapps\common\PCL2\.minecraft\versions\Mechanomania\saves\新的世界\level.dat'
with gzip.open(path, 'rb') as fp:
    root = read_nbt(fp)
data = root[2].get('Data', root[2])
keys = ['LevelName', 'spawnX', 'spawnZ', 'spawnY', 'GameType']
for k in keys:
    if k in data:
        print(f'{k}: {data[k]}')
