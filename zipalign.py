import sys
import struct
import zipfile

def align_zip(in_path, out_path, alignment=4):
    with open(in_path, 'rb') as fin:
        with zipfile.ZipFile(fin, 'r') as zin:
            entries = []
            for info in zin.infolist():
                fin.seek(info.header_offset)
                hdr = fin.read(30)
                sig, ver, res, flags, method, time, date, crc, csize, usize, fn_len, extra_len = struct.unpack(
                    zipfile.structFileHeader, hdr
                )
                fin.seek(info.header_offset + 30 + fn_len + extra_len)
                raw_data = fin.read(info.compress_size)
                assert len(raw_data) == info.compress_size, f'Failed to read {info.filename}'
                entries.append((info, raw_data))

    with open(out_path, 'wb') as fout:
        central_dir = []
        for info, raw_data in entries:
            local_header_offset = fout.tell()
            name_bytes = info.filename.encode('utf-8')

            # Clean flag bits: strip bit 3 (data descriptor) since we write sizes directly into the header
            flags = info.flag_bits & ~0x0008

            hdr_size_no_extra = 30 + len(name_bytes)
            pad = 0
            # If stored uncompressed (e.g. resources.arsc), align raw data to alignment (4 bytes)
            if info.compress_type == 0:
                pad = (alignment - ((local_header_offset + hdr_size_no_extra) % alignment)) % alignment

            extra = b'\x00' * pad

            dos_time = (info.date_time[3] << 11 | info.date_time[4] << 5 | info.date_time[5] // 2) if len(info.date_time) >= 6 else 0
            dos_date = ((info.date_time[0] - 1980) << 9 | info.date_time[1] << 5 | info.date_time[2]) if len(info.date_time) >= 3 else 0

            # Write local header (30 bytes)
            local_hdr = struct.pack(
                zipfile.structFileHeader,
                zipfile.stringFileHeader,
                info.extract_version,
                info.reserved,
                flags,
                info.compress_type,
                dos_time,
                dos_date,
                info.CRC,
                info.compress_size,
                info.file_size,
                len(name_bytes),
                len(extra)
            )
            fout.write(local_hdr)
            fout.write(name_bytes)
            fout.write(extra)

            if info.compress_type == 0:
                assert fout.tell() % alignment == 0, f'Unaligned entry {info.filename} at {fout.tell()}'

            fout.write(raw_data)

            # Record info for central directory
            central_dir.append((info, flags, name_bytes, local_header_offset, dos_time, dos_date))

        cd_offset = fout.tell()
        for info, flags, name_bytes, local_offset, dos_time, dos_date in central_dir:
            cd_hdr = struct.pack(
                zipfile.structCentralDir,
                zipfile.stringCentralDir,
                info.create_version,
                info.create_system,
                info.extract_version,
                info.reserved,
                flags,
                info.compress_type,
                dos_time,
                dos_date,
                info.CRC,
                info.compress_size,
                info.file_size,
                len(name_bytes),
                0, # extra field len in central dir
                0, # comment len
                0, # disk start
                info.internal_attr,
                info.external_attr,
                local_offset
            )
            fout.write(cd_hdr)
            fout.write(name_bytes)

        cd_size = fout.tell() - cd_offset
        eocd = struct.pack(
            zipfile.structEndArchive,
            zipfile.stringEndArchive,
            0, # disk number
            0, # start disk
            len(central_dir), # entries on disk
            len(central_dir), # total entries
            cd_size,
            cd_offset,
            0 # comment len
        )
        fout.write(eocd)
    print(f'Aligned {len(entries)} entries, wrote {out_path}')

if __name__ == '__main__':
    align_zip(sys.argv[1], sys.argv[2])
