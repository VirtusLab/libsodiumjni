package libsodiumjni.tests;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the names of the DLLs a 64-bit PE file (Windows DLL) imports, from its import and delay-load
 * import tables, like {@code dumpbin /dependents} (which is only available from a Visual Studio
 * environment).
 */
final class PeImports {

    private PeImports() {}

    private static final int PE32_PLUS_MAGIC = 0x20b;
    private static final int IMPORT_DIRECTORY = 1;
    private static final int DELAY_IMPORT_DIRECTORY = 13;

    static List<String> importedDlls(byte[] content) {
        ByteBuffer buf = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        if (content.length < 0x40 || content[0] != 'M' || content[1] != 'Z')
            throw new IllegalArgumentException("Not a PE file (no MZ header)");
        int pe = buf.getInt(0x3c);
        if (buf.getInt(pe) != 0x00004550)
            throw new IllegalArgumentException("Not a PE file (no PE signature)");

        int coffHeader = pe + 4;
        int numberOfSections = Short.toUnsignedInt(buf.getShort(coffHeader + 2));
        int sizeOfOptionalHeader = Short.toUnsignedInt(buf.getShort(coffHeader + 16));
        int optionalHeader = coffHeader + 20;
        int magic = Short.toUnsignedInt(buf.getShort(optionalHeader));
        if (magic != PE32_PLUS_MAGIC)
            throw new IllegalArgumentException("Not a 64-bit PE file (optional header magic " + Integer.toHexString(magic) + ")");
        // PE32+: the data directories (RVA and size, 8 bytes each) start at offset 112
        int dataDirectories = optionalHeader + 112;
        int sectionTable = optionalHeader + sizeOfOptionalHeader;

        List<String> dlls = new ArrayList<>();

        // IMAGE_IMPORT_DESCRIPTOR: 20 bytes, name RVA at offset 12, ends with an all-zero entry
        int importRva = buf.getInt(dataDirectories + 8 * IMPORT_DIRECTORY);
        if (importRva != 0) {
            for (int desc = rvaToOffset(buf, sectionTable, numberOfSections, importRva); ; desc += 20) {
                int nameRva = buf.getInt(desc + 12);
                if (nameRva == 0) break;
                dlls.add(readCString(content, rvaToOffset(buf, sectionTable, numberOfSections, nameRva)));
            }
        }

        // IMAGE_DELAYLOAD_DESCRIPTOR: 32 bytes, name RVA at offset 4, ends with an all-zero entry
        int delayImportRva = buf.getInt(dataDirectories + 8 * DELAY_IMPORT_DIRECTORY);
        if (delayImportRva != 0) {
            for (int desc = rvaToOffset(buf, sectionTable, numberOfSections, delayImportRva); ; desc += 32) {
                int nameRva = buf.getInt(desc + 4);
                if (nameRva == 0) break;
                dlls.add(readCString(content, rvaToOffset(buf, sectionTable, numberOfSections, nameRva)));
            }
        }

        return dlls;
    }

    /** File offset of a relative virtual address, from the section headers (40 bytes each) */
    private static int rvaToOffset(ByteBuffer buf, int sectionTable, int numberOfSections, int rva) {
        for (int i = 0; i < numberOfSections; i++) {
            int section = sectionTable + 40 * i;
            int virtualSize = buf.getInt(section + 8);
            int virtualAddress = buf.getInt(section + 12);
            int sizeOfRawData = buf.getInt(section + 16);
            int pointerToRawData = buf.getInt(section + 20);
            int size = Math.max(virtualSize, sizeOfRawData);
            if (Integer.compareUnsigned(rva, virtualAddress) >= 0
                    && Integer.compareUnsigned(rva, virtualAddress + size) < 0)
                return rva - virtualAddress + pointerToRawData;
        }
        throw new IllegalArgumentException("RVA " + Integer.toHexString(rva) + " isn't in any section");
    }

    private static String readCString(byte[] content, int offset) {
        int end = offset;
        while (end < content.length && content[end] != 0) end++;
        return new String(content, offset, end - offset, StandardCharsets.US_ASCII);
    }
}
