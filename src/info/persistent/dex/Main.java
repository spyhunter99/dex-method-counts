/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.persistent.dex;

import com.android.dexdeps.DexData;
import com.android.dexdeps.DexDataException;
import com.github.spyhunter99.DynamicLoader;
import com.github.spyhunter99.model.CountData;
import com.github.spyhunter99.model.Metric;
import com.github.spyhunter99.model.Node;
import com.github.spyhunter99.writers.FormattedText;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class Main {

    private boolean includeClasses;
    private String packageFilter;
    private int maxDepth = Integer.MAX_VALUE;
    private DexMethodCounts.Filter filter = DexMethodCounts.Filter.ALL;

    public static void main(String[] args) {
        Main main = new Main();
        main.run(args);
    }

    void run(String[] args) {
        try {
            String[] inputFileNames = parseArgs(args);
            int overallCount = 0;
            List<CountData> data =new ArrayList<CountData>();
            for (String fileName : collectFileNames(inputFileNames)) {
                System.out.println("Processing " + fileName);
                DexCount fields= new DexFieldCounts();
                DexCount methods = new DexMethodCounts();

                Map<String, RandomAccessFile> dexFiles = openInputFiles(fileName);

                for (String dexFilenName : dexFiles.keySet()) {
                    RandomAccessFile dexFile = dexFiles.get(dexFilenName);
                    DexData dexData = new DexData(dexFilenName, dexFile);
                    try {
                        dexData.load();
                        CountData generate = fields.generate(dexData, includeClasses, packageFilter, maxDepth, filter);

                        CountData generate1 = methods.generate(dexData, includeClasses, packageFilter, maxDepth, filter);
                        generate.fileName=fileName;
                        generate1.fileName=fileName;
                        data.add(generate);
                        data.add(generate1);
                        dexFile.close();
                        continue;
                    } catch (DexDataException d){
                        //not a dex'd file, plan b;
                    }
                    try {
                        data.add(DynamicLoader.getClasses(new File(fileName)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }

            //then output the data

            processOutput(data);



        } catch (UsageException ue) {
            usage();
            System.exit(2);
        } catch (IOException ioe) {
            if (ioe.getMessage() != null) {
                System.err.println("Failed: " + ioe);
            }
            System.exit(1);
        }
    }

    private void processOutput(List<CountData> data) {
        System.out.println(FormattedText.getReport(data));

    }



    /**
     * Opens an input file, which could be a .dex or a .jar/.apk with a
     * classes.dex inside.  If the latter, we extract the contents to a
     * temporary file.
     */
    Map<String,RandomAccessFile> openInputFiles(String fileName) throws IOException {
        Map<String,RandomAccessFile> dexFiles = new HashMap<String, RandomAccessFile>();

        openInputFileAsZip(fileName, dexFiles);
        if (dexFiles.size() == 0) {
            File inputFile = new File(fileName);
            RandomAccessFile dexFile = new RandomAccessFile(inputFile, "r");
            dexFiles.put(fileName, dexFile);
        }

        return dexFiles;
    }

    /**
     * Tries to open an input file as a Zip archive (jar/apk) with a
     * "classes.dex" inside.
     */
    void openInputFileAsZip(String fileName, Map<String,RandomAccessFile> dexFiles) throws IOException {
        ZipFile zipFile;

        // Try it as a zip file.
        try {
            zipFile = new ZipFile(fileName);
        } catch (FileNotFoundException fnfe) {
            // not found, no point in retrying as non-zip.
            System.err.println("Unable to open '" + fileName + "': " +
                    fnfe.getMessage());
            throw fnfe;
        } catch (ZipException ze) {
            // not a zip
            return;
        }

        // Open and add all files matching "classes.*\.dex" in the zip file.
        for (ZipEntry entry : Collections.list(zipFile.entries())) {
            if (entry.getName().matches("classes.*\\.dex")) {
                dexFiles.put(entry.getName(), openDexFile(zipFile, entry));
            }
        }

        zipFile.close();
    }

    RandomAccessFile openDexFile(ZipFile zipFile, ZipEntry entry) throws IOException  {
        // We know it's a zip; see if there's anything useful inside.  A
        // failure here results in some type of IOException (of which
        // ZipException is a subclass).
        InputStream zis = zipFile.getInputStream(entry);

        // Create a temp file to hold the DEX data, open it, and delete it
        // to ensure it doesn't hang around if we fail.
        File tempFile = File.createTempFile("dexdeps", ".dex");
        RandomAccessFile dexFile = new RandomAccessFile(tempFile, "rw");
        tempFile.delete();

        // Copy all data from input stream to output file.
        byte copyBuf[] = new byte[32768];
        int actual;

        while (true) {
            actual = zis.read(copyBuf);
            if (actual == -1)
                break;

            dexFile.write(copyBuf, 0, actual);
        }

        dexFile.seek(0);

        return dexFile;
    }

    private String[] parseArgs(String[] args) {
        int idx;

        for (idx = 0; idx < args.length; idx++) {
            String arg = args[idx];

            if (arg.equals("--") || !arg.startsWith("--")) {
                break;
            } else if (arg.equals("--count-fields")) {

            } else if (arg.equals("--include-classes")) {
                includeClasses = true;
            } else if (arg.startsWith("--package-filter=")) {
                packageFilter = arg.substring(arg.indexOf('=') + 1);
            } else if (arg.startsWith("--max-depth=")) {
                maxDepth =
                    Integer.parseInt(arg.substring(arg.indexOf('=') + 1));
            } else if (arg.startsWith("--filter=")) {
                filter = Enum.valueOf(
                    DexMethodCounts.Filter.class,
                    arg.substring(arg.indexOf('=') + 1).toUpperCase());
            } else if (arg.startsWith("--output-style")) {

            } else {
                System.err.println("Unknown option '" + arg + "'");
                throw new UsageException();
            }
        }

        // We expect at least one more argument (file name).
        int fileCount = args.length - idx;
        if (fileCount == 0) {
            throw new UsageException();
        }
        String[] inputFileNames = new String[fileCount];
        System.arraycopy(args, idx, inputFileNames, 0, fileCount);
        return inputFileNames;
    }

    private void usage() {
        System.err.print(
            "DEX per-package/class method counts v1.5\n" +
            "Usage: dex-method-counts [options] <file.{dex,apk,jar,directory}> ...\n" +
            "Options:\n" +
            "  --count-fields\n" +
            "  --include-classes\n" +
            "  --package-filter=com.foo.bar\n" +
            "  --max-depth=N\n" +
            "  --filter=ALL|DEFINED_ONLY|REFERENCED_ONLY\n" +
            "  --output-style=FLAT|TREE\n"
        );
    }

    /**
     * Checks if input files array contain directories and
     * adds it's contents to the file list if so.
     * Otherwise just adds a file to the list.
     *
     * @return a List of file names to process
     */
    private List<String> collectFileNames(String[] inputFileNames) {
        List<String> fileNames = new ArrayList<String>();
        for (String inputFileName : inputFileNames) {
            File file = new File(inputFileName);
            if (file.isDirectory()) {
                String dirPath = file.getAbsolutePath();
                for (String fileInDir: file.list()){
                    fileNames.add(dirPath + File.separator + fileInDir);
                }
            } else {
                fileNames.add(inputFileName);
            }
        }
        return fileNames;
    }

    private static class UsageException extends RuntimeException {}
}
