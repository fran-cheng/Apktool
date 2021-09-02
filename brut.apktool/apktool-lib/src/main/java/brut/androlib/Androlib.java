/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.androlib;

import brut.androlib.meta.MetaInfo;
import brut.androlib.meta.UsesFramework;
import brut.androlib.res.AndrolibResources;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.data.ResUnknownFiles;
import brut.common.InvalidUnknownFileException;
import brut.common.RootUnknownFileException;
import brut.common.TraversalUnknownFileException;
import brut.directory.ExtFile;
import brut.androlib.res.xml.ResXmlPatcher;
import brut.androlib.src.SmaliBuilder;
import brut.androlib.src.SmaliDecoder;
import brut.common.BrutException;
import brut.directory.*;
import brut.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jf.dexlib2.iface.DexFile;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 核心解析类
 * 主要方法:1.decodeRawFiles 2.decodeManifestWithResources 3.decodeResourcesFull 4.decodeSourcesSmali
 * 基本都能见名思意, 具体实现在AndrolibResources.java
 */
public class Androlib {
    /**
     * Resources.arsc 文件解析
     */
    private final AndrolibResources mAndRes = new AndrolibResources();
    /**
     * Res未知文件
     */
    protected final ResUnknownFiles mResUnknownFiles = new ResUnknownFiles();
    /**
     * APK选项
     */
    public ApkOptions apkOptions;
    private int mMinSdkVersion = 0;

    public Androlib() {
        this(new ApkOptions());
    }

    public Androlib(ApkOptions apkOptions) {
        this.apkOptions = apkOptions;
        mAndRes.apkOptions = apkOptions;
    }

    /**
     * 获取ResTable
     *
     * @param apkFile ApkFile
     * @return ResTable
     * @throws AndrolibException 自定义异常
     */
    public ResTable getResTable(ExtFile apkFile)
        throws AndrolibException {
        return mAndRes.getResTable(apkFile, true);
    }

    /**
     * 获取ResTable
     *
     * @param apkFile     ApkFile
     * @param loadMainPkg boolean 是否有resources.arsc
     * @return ResTable
     * @throws AndrolibException 自定义异常
     */
    public ResTable getResTable(ExtFile apkFile, boolean loadMainPkg)
        throws AndrolibException {
        return mAndRes.getResTable(apkFile, loadMainPkg);
    }

    /**
     * 获取minSdk版本
     *
     * @return int
     */
    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }

    /**
     * 拷贝原始 dex
     *
     * @param apkFile  ApkFile
     * @param outDir   outDir
     * @param filename 拷贝filename
     * @throws AndrolibException 自定义异常
     */
    public void decodeSourcesRaw(ExtFile apkFile, File outDir, String filename)
        throws AndrolibException {
        try {
            LOGGER.info("Copying raw " + filename + " file...");
            apkFile.getDirectory().copyToDir(outDir, filename);
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 将dex解码为smali
     *
     * @param apkFile  apkFile
     * @param outDir   outDir
     * @param filename filename
     * @param bakDeb   bakDeb
     * @param apiLevel apiLevel
     * @throws AndrolibException 自定义异常
     */
    public void decodeSourcesSmali(File apkFile, File outDir, String filename, boolean bakDeb, int apiLevel)
        throws AndrolibException {
        try {
            File smaliDir;
            if (filename.equalsIgnoreCase("classes.dex")) {
                smaliDir = new File(outDir, SMALI_DIRNAME);
            } else {
                smaliDir = new File(outDir, SMALI_DIRNAME + "_" + filename.substring(0, filename.indexOf(".")));
            }
            OS.rmdir(smaliDir);
            smaliDir.mkdirs();
            LOGGER.info("Baksmaling " + filename + "...");
            DexFile dexFile = SmaliDecoder.decode(apkFile, smaliDir, filename, bakDeb, apiLevel);
            int minSdkVersion = dexFile.getOpcodes().api;
            if (mMinSdkVersion == 0 || mMinSdkVersion > minSdkVersion) {
                mMinSdkVersion = minSdkVersion;
            }
        } catch (BrutException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 拷贝AndroidManifest.xml
     *
     * @param apkFile apkFile
     * @param outDir  outDir
     * @throws AndrolibException 自定义异常
     */
    public void decodeManifestRaw(ExtFile apkFile, File outDir)
        throws AndrolibException {
        try {
            LOGGER.info("Copying raw manifest...");
            apkFile.getDirectory().copyToDir(outDir, APK_MANIFEST_FILENAMES);
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 解码AndroidManifest.xml
     *
     * @param apkFile  apkFile
     * @param outDir   outDir
     * @param resTable resTable
     * @throws AndrolibException 自定义异常
     */
    public void decodeManifestFull(ExtFile apkFile, File outDir, ResTable resTable)
        throws AndrolibException {
        mAndRes.decodeManifest(resTable, apkFile, outDir);
    }

    /**
     * 拷贝原始 "resources.arsc", "AndroidManifest.xml", "res"
     *
     * @param apkFile apkFile
     * @param outDir  outDir
     * @throws AndrolibException
     */
    public void decodeResourcesRaw(ExtFile apkFile, File outDir)
        throws AndrolibException {
        try {
            LOGGER.info("Copying raw resources...");
            apkFile.getDirectory().copyToDir(outDir, APK_RESOURCES_FILENAMES);
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 解码 resources.arsc 资源
     *
     * @param apkFile  apkFile
     * @param outDir   outDir
     * @param resTable resTable
     * @throws AndrolibException 自定义异常
     */
    public void decodeResourcesFull(ExtFile apkFile, File outDir, ResTable resTable)
        throws AndrolibException {
        mAndRes.decode(resTable, apkFile, outDir);
    }

    /**
     * 根据 resources.arsc 解码AndroidManifest.xml
     *
     * @param apkFile  apkFile
     * @param outDir   outDir
     * @param resTable resTable
     * @throws AndrolibException 自定义异常
     */
    public void decodeManifestWithResources(ExtFile apkFile, File outDir, ResTable resTable)
        throws AndrolibException {
        mAndRes.decodeManifestWithResources(resTable, apkFile, outDir);
    }

    /**
     * 拷贝资源 如assets,lib,libs,kotlin
     *
     * @param apkFile         apkFile
     * @param outDir          outDir
     * @param decodeAssetMode 模式，是否解码/复制assets
     * @throws AndrolibException 自定义异常
     */
    public void decodeRawFiles(ExtFile apkFile, File outDir, short decodeAssetMode)
        throws AndrolibException {
        LOGGER.info("Copying assets and libs...");
        try {
            Directory in = apkFile.getDirectory();

            if (decodeAssetMode == ApkDecoder.DECODE_ASSETS_FULL) {
                if (in.containsDir("assets")) {
                    in.copyToDir(outDir, "assets");
                }
            }
            if (in.containsDir("lib")) {
                in.copyToDir(outDir, "lib");
            }
            if (in.containsDir("libs")) {
                in.copyToDir(outDir, "libs");
            }
            if (in.containsDir("kotlin")) {
                in.copyToDir(outDir, "kotlin");
            }
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 收集不压缩文件
     *
     * @param apkFile                 apkFile
     * @param uncompressedFilesOrExts uncompressedFilesOrExts
     * @throws AndrolibException 自定义异常
     */
    public void recordUncompressedFiles(ExtFile apkFile, Collection<String> uncompressedFilesOrExts) throws AndrolibException {
        try {
            Directory unk = apkFile.getDirectory();
            Set<String> files = unk.getFiles(true);

            for (String file : files) {
                if (isAPKFileNames(file) && unk.getCompressionLevel(file) == 0) {
                    String ext = "";
                    if (unk.getSize(file) != 0) {
                        ext = FilenameUtils.getExtension(file);
                    }

                    if (ext.isEmpty() || !NO_COMPRESS_PATTERN.matcher(ext).find()) {
                        ext = file;
                    }
                    if (!uncompressedFilesOrExts.contains(ext)) {
                        uncompressedFilesOrExts.add(ext);
                    }
                }
            }
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 是否是Apk标准所有文件名
     *
     * @param file Apk标准所有文件名
     * @return boolean
     */
    private boolean isAPKFileNames(String file) {
        for (String apkFile : APK_STANDARD_ALL_FILENAMES) {
            if (apkFile.equals(file) || file.startsWith(apkFile + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拷贝未知文件
     *
     * @param apkFile apkFile
     * @param outDir  outDir
     * @throws AndrolibException 自定义异常
     */
    public void decodeUnknownFiles(ExtFile apkFile, File outDir)
        throws AndrolibException {
        LOGGER.info("Copying unknown files...");
        File unknownOut = new File(outDir, UNK_DIRNAME);
        try {
            Directory unk = apkFile.getDirectory();

            // loop all items in container recursively, ignoring any that are pre-defined by aapt
//            递归循环容器中的所有项，忽略aapt预定义的任何项
            Set<String> files = unk.getFiles(true);
            for (String file : files) {
                if (!isAPKFileNames(file) && !file.endsWith(".dex")) {
//                    非标准APK文件，非dex后缀，拷贝到 unknown
                    // copy file out of archive into special "unknown" folder
                    unk.copyToDir(unknownOut, file);
                    // lets record the name of the file, and its compression type
                    // so that we may re-include it the same way
//                    添加的未知Res，回编的时候原样塞回去
                    mResUnknownFiles.addUnknownFileInfo(file, String.valueOf(unk.getCompressionLevel(file)));
                }
            }
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 生成Original文件夹及相关内容
     *
     * @param apkFile apkFile
     * @param outDir  outDir
     * @throws AndrolibException 自定义异常
     */
    public void writeOriginalFiles(ExtFile apkFile, File outDir)
        throws AndrolibException {
        LOGGER.info("Copying original files...");
        File originalDir = new File(outDir, "original");
        if (!originalDir.exists()) {
            originalDir.mkdirs();
        }

        try {
            Directory in = apkFile.getDirectory();
            if (in.containsFile("AndroidManifest.xml")) {
                in.copyToDir(originalDir, "AndroidManifest.xml");
            }
            if (in.containsDir("META-INF")) {
                in.copyToDir(originalDir, "META-INF");

                if (in.containsDir("META-INF/services")) {
                    // If the original APK contains the folder META-INF/services folder
                    // that is used for service locators (like coroutines on android),
                    // copy it to the destination folder so it does not get dropped.
                    LOGGER.info("Copying META-INF/services directory");
                    in.copyToDir(outDir, "META-INF/services");
                }
            }
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 生成apktool.yml
     *
     * @param mOutDir mOutDir
     * @param meta    meta
     * @throws AndrolibException 自定义异常
     */
    public void writeMetaFile(File mOutDir, MetaInfo meta)
        throws AndrolibException {
        try {
            meta.save(new File(mOutDir, "apktool.yml"));
        } catch (IOException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 读取apktool.yml
     *
     * @param appDir appDir
     * @return MetaInfo
     * @throws AndrolibException 自定义异常
     */
    public MetaInfo readMetaFile(ExtFile appDir)
        throws AndrolibException {
        try (
            InputStream in = appDir.getDirectory().getFileInput("apktool.yml")
        ) {
            return MetaInfo.load(in);
        } catch (DirectoryException | IOException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 回编
     *
     * @param appDir  appDir
     * @param outFile outFile
     * @throws BrutException 自定义异常
     */
    public void build(File appDir, File outFile) throws BrutException {
        build(new ExtFile(appDir), outFile);
    }

    /**
     * 回编
     *
     * @param appDir  appDir 回编工作根路径
     * @param outFile outFile 输出文件路径
     * @throws BrutException 自定义异常
     */
    public void build(ExtFile appDir, File outFile)
        throws BrutException {
        LOGGER.info("Using Apktool " + Androlib.getVersion());

//        加载apktool.yml
        MetaInfo meta = readMetaFile(appDir);
        apkOptions.isFramework = meta.isFrameworkApk;
        apkOptions.resourcesAreCompressed = meta.compressionType;
        apkOptions.doNotCompress = meta.doNotCompress;

        mAndRes.setSdkInfo(meta.sdkInfo);
        mAndRes.setPackageId(meta.packageInfo);
        mAndRes.setPackageRenamed(meta.packageInfo);
        mAndRes.setVersionInfo(meta.versionInfo);
        mAndRes.setSharedLibrary(meta.sharedLibrary);
        mAndRes.setSparseResources(meta.sparseResources);

        if (meta.sdkInfo != null && meta.sdkInfo.get("minSdkVersion") != null) {
//            赋值minSdkVersion
            String minSdkVersion = meta.sdkInfo.get("minSdkVersion");
            mMinSdkVersion = mAndRes.getMinSdkVersionFromAndroidCodename(meta, minSdkVersion);
        }

        if (outFile == null) {
            String outFileName = meta.apkFileName;
            outFile = new File(appDir, "dist" + File.separator + (outFileName == null ? "out.apk" : outFileName));
        }

//        生成回编 build文件夹
        new File(appDir, APK_DIRNAME).mkdirs();
        File manifest = new File(appDir, "AndroidManifest.xml");
        File manifestOriginal = new File(appDir, "AndroidManifest.xml.orig");

//        构建dex文件
        buildSources(appDir);
        buildNonDefaultSources(appDir);
        buildManifestFile(appDir, manifest, manifestOriginal);
        buildResources(appDir, meta.usesFramework);
        buildLibs(appDir);
        buildCopyOriginalFiles(appDir);
        buildApk(appDir, outFile);

        // we must go after the Apk is built, and copy the files in via Zip
        // this is because Aapt won't add files it doesn't know (ex unknown files)
        buildUnknownFiles(appDir, outFile, meta);

        // we copied the AndroidManifest.xml to AndroidManifest.xml.orig so we can edit it
        // lets restore the unedited one, to not change the original
        if (manifest.isFile() && manifest.exists() && manifestOriginal.isFile()) {
            try {
                if (new File(appDir, "AndroidManifest.xml").delete()) {
                    FileUtils.moveFile(manifestOriginal, manifest);
                }
            } catch (IOException ex) {
                throw new AndrolibException(ex.getMessage());
            }
        }
        LOGGER.info("Built apk...");
    }

    private void buildManifestFile(File appDir, File manifest, File manifestOriginal)
        throws AndrolibException {

        // If we decoded in "raw", we cannot patch AndroidManifest
        if (new File(appDir, "resources.arsc").exists()) {
            return;
        }
        if (manifest.isFile() && manifest.exists()) {
            try {
                if (manifestOriginal.exists()) {
                    manifestOriginal.delete();
                }
                FileUtils.copyFile(manifest, manifestOriginal);
                ResXmlPatcher.fixingPublicAttrsInProviderAttributes(manifest);
            } catch (IOException ex) {
                throw new AndrolibException(ex.getMessage());
            }
        }
    }

    /**
     * 构建 dex 文件
     *
     * @param appDir appDir
     * @throws AndrolibException 自定义异常
     */
    public void buildSources(File appDir)
        throws AndrolibException {
        if (!buildSourcesRaw(appDir, "classes.dex") && !buildSourcesSmali(appDir, "smali", "classes.dex")) {
            LOGGER.warning("Could not find sources");
        }
    }

    public void buildNonDefaultSources(ExtFile appDir)
        throws AndrolibException {
        try {
            // loop through any smali_ directories for multi-dex apks
            Map<String, Directory> dirs = appDir.getDirectory().getDirs();
            for (Map.Entry<String, Directory> directory : dirs.entrySet()) {
                String name = directory.getKey();
                if (name.startsWith("smali_")) {
                    String filename = name.substring(name.indexOf("_") + 1) + ".dex";

                    if (!buildSourcesRaw(appDir, filename) && !buildSourcesSmali(appDir, name, filename)) {
                        LOGGER.warning("Could not find sources");
                    }
                }
            }

            // loop through any classes#.dex files for multi-dex apks
            File[] dexFiles = appDir.listFiles();
            if (dexFiles != null) {
                for (File dex : dexFiles) {

                    // skip classes.dex because we have handled it in buildSources()
                    if (dex.getName().endsWith(".dex") && !dex.getName().equalsIgnoreCase("classes.dex")) {
                        buildSourcesRaw(appDir, dex.getName());
                    }
                }
            }
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 拷贝dex文件
     * 如果filename存在，拷贝filename文件
     *
     * @param appDir   appDir
     * @param filename filename
     * @return 拷贝结果，不存在false
     * @throws AndrolibException 自定义异常
     */
    public boolean buildSourcesRaw(File appDir, String filename)
        throws AndrolibException {
        File working = new File(appDir, filename);
        if (!working.exists()) {
            return false;
        }
        File stored = new File(appDir, APK_DIRNAME + "/" + filename);
        if (apkOptions.forceBuildAll || isModified(working, stored)) {
            LOGGER.info("Copying " + appDir.toString() + " " + filename + " file...");
            try {
                BrutIO.copyAndClose(new FileInputStream(working), new FileOutputStream(stored));
                return true;
            } catch (IOException ex) {
                throw new AndrolibException(ex);
            }
        }
        return true;
    }

    /**
     * 回编，将smali回编成dex
     *
     * @param appDir   appDir 回编根路径
     * @param folder   folder  文件夹名  如 smali
     * @param filename filename 生成的dex文件名
     * @return filename是否存在
     * @throws AndrolibException 自定义异常
     */
    public boolean buildSourcesSmali(File appDir, String folder, String filename)
        throws AndrolibException {
        ExtFile smaliDir = new ExtFile(appDir, folder);
        if (!smaliDir.exists()) {
            return false;
        }
//        生成的dex存放路径
        File dex = new File(appDir, APK_DIRNAME + "/" + filename);
        if (!apkOptions.forceBuildAll) {
            LOGGER.info("Checking whether sources has changed...");
        }
        if (apkOptions.forceBuildAll || isModified(smaliDir, dex)) {
            LOGGER.info("Smaling " + folder + " folder into " + filename + "...");
            dex.delete();
//            具体的回编操作
            SmaliBuilder.build(smaliDir, dex, apkOptions.forceApi > 0 ? apkOptions.forceApi : mMinSdkVersion);
        }
        return true;
    }

    /**
     * 回编资源生成 resources.arsc
     *
     * @param appDir        appDir
     * @param usesFramework usesFramework
     * @throws BrutException 自定义异常
     */
    public void buildResources(ExtFile appDir, UsesFramework usesFramework)
        throws BrutException {
        if (!buildResourcesRaw(appDir) && !buildResourcesFull(appDir, usesFramework)
            && !buildManifest(appDir, usesFramework)) {
            LOGGER.warning("Could not find resources");
        }
    }

    /**
     * 如果resources.arsc存在
     * 拷贝 "resources.arsc", "AndroidManifest.xml", "res"
     *
     * @param appDir appDir
     * @return 拷贝结果，不存在false
     * @throws AndrolibException 自定义异常
     */
    public boolean buildResourcesRaw(ExtFile appDir)
        throws AndrolibException {
        try {
            if (!new File(appDir, "resources.arsc").exists()) {
                return false;
            }
            File apkDir = new File(appDir, APK_DIRNAME);
            if (!apkOptions.forceBuildAll) {
                LOGGER.info("Checking whether resources has changed...");
            }
            if (apkOptions.forceBuildAll || isModified(newFiles(APK_RESOURCES_FILENAMES, appDir),
                newFiles(APK_RESOURCES_FILENAMES, apkDir))) {
                LOGGER.info("Copying raw resources...");
                appDir.getDirectory().copyToDir(apkDir, APK_RESOURCES_FILENAMES);
            }
            return true;
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 构建 resources.arsc
     *
     * @param appDir        appDir
     * @param usesFramework usesFramework
     * @return 构建结果，不存在"res" 返回false
     * @throws AndrolibException 自定义异常
     */
    public boolean buildResourcesFull(File appDir, UsesFramework usesFramework)
        throws AndrolibException {
        try {
            if (!new File(appDir, "res").exists()) {
                return false;
            }
            if (!apkOptions.forceBuildAll) {
                LOGGER.info("Checking whether resources has changed...");
            }
            File apkDir = new File(appDir, APK_DIRNAME);
            File resourceFile = new File(apkDir.getParent(), "resources.zip");

            if (apkOptions.forceBuildAll || isModified(newFiles(APP_RESOURCES_FILENAMES, appDir),
                newFiles(APK_RESOURCES_FILENAMES, apkDir)) || (apkOptions.isAapt2() && !isFile(resourceFile))) {
                LOGGER.info("Building resources...");

                if (apkOptions.debugMode) {
                    if (apkOptions.isAapt2()) {
                        LOGGER.info("Using aapt2 - setting 'debuggable' attribute to 'true' in AndroidManifest.xml");
                        ResXmlPatcher.setApplicationDebugTagTrue(new File(appDir, "AndroidManifest.xml"));
                    } else {
                        ResXmlPatcher.removeApplicationDebugTag(new File(appDir, "AndroidManifest.xml"));
                    }
                }

                File apkFile = File.createTempFile("APKTOOL", null);
                apkFile.delete();
                resourceFile.delete();

                File ninePatch = new File(appDir, "9patch");
                if (!ninePatch.exists()) {
                    ninePatch = null;
                }
//                使用aapt生成 resources.arsc
                mAndRes.aaptPackage(apkFile, new File(appDir,
                        "AndroidManifest.xml"), new File(appDir, "res"),
                    ninePatch, null, parseUsesFramework(usesFramework));

                Directory tmpDir = new ExtFile(apkFile).getDirectory();

                // Sometimes an application is built with a resources.arsc file with no resources,
                // Apktool assumes it will have a rebuilt arsc file, when it doesn't. So if we
                // encounter a copy error, move to a warning and continue on. (#1730)
//                有时应用程序是用资源构建的。没有资源的Arsc文件， Apktool假设它将有一个重建的arsc文件。当它没有，所以如果我们遇到复制错误，移动到警告并继续。(# 1730)
                try {
                    tmpDir.copyToDir(apkDir,
                        tmpDir.containsDir("res") ? APK_RESOURCES_FILENAMES
                            : APK_RESOURCES_WITHOUT_RES_FILENAMES);
                } catch (DirectoryException ex) {
                    LOGGER.warning(ex.getMessage());
                }

                // delete tmpDir
                apkFile.delete();
            }
            return true;
        } catch (IOException | BrutException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 从build/apk
     * 拷贝AndroidManifest.xml
     * 到appDir
     *
     * @param appDir appDir
     * @return 拷贝结果
     * @throws AndrolibException 自定义异常
     */
    public boolean buildManifestRaw(ExtFile appDir)
        throws AndrolibException {
        try {
            File apkDir = new File(appDir, APK_DIRNAME);
            LOGGER.info("Copying raw AndroidManifest.xml...");
            appDir.getDirectory().copyToDir(apkDir, APK_MANIFEST_FILENAMES);
            return true;
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 构建AndroidManifest.xml
     *
     * @param appDir        appDir
     * @param usesFramework usesFramework
     * @return 当appDir 下不存在 AndroidManifest.xml 返回false
     * @throws BrutException 自定义异常
     */
    public boolean buildManifest(ExtFile appDir, UsesFramework usesFramework)
        throws BrutException {
        try {
            if (!new File(appDir, "AndroidManifest.xml").exists()) {
                return false;
            }
            if (!apkOptions.forceBuildAll) {
                LOGGER.info("Checking whether resources has changed...");
            }

            File apkDir = new File(appDir, APK_DIRNAME);

            if (apkOptions.forceBuildAll || isModified(newFiles(APK_MANIFEST_FILENAMES, appDir),
                newFiles(APK_MANIFEST_FILENAMES, apkDir))) {
                LOGGER.info("Building AndroidManifest.xml...");

                File apkFile = File.createTempFile("APKTOOL", null);
                apkFile.delete();

                File ninePatch = new File(appDir, "9patch");
                if (!ninePatch.exists()) {
                    ninePatch = null;
                }

//                使用aapt
                mAndRes.aaptPackage(apkFile, new File(appDir,
                        "AndroidManifest.xml"), null, ninePatch, null,
                    parseUsesFramework(usesFramework));

                Directory tmpDir = new ExtFile(apkFile).getDirectory();
                tmpDir.copyToDir(apkDir, APK_MANIFEST_FILENAMES);
            }
            return true;
        } catch (IOException | DirectoryException ex) {
            throw new AndrolibException(ex);
        } catch (AndrolibException ex) {
            LOGGER.warning("Parse AndroidManifest.xml failed, treat it as raw file.");
            return buildManifestRaw(appDir);
        }
    }

    /**
     * 构建其他libs，标准的apk文件
     *
     * @param appDir appDir
     * @throws AndrolibException 自定义异常
     */
    public void buildLibs(File appDir) throws AndrolibException {
        buildLibrary(appDir, "lib");
        buildLibrary(appDir, "libs");
        buildLibrary(appDir, "kotlin");
        buildLibrary(appDir, "META-INF/services");
    }

    /**
     * 拷贝文件folder到  build/apk
     *
     * @param appDir appDir
     * @param folder folder
     * @throws AndrolibException
     */

    public void buildLibrary(File appDir, String folder) throws AndrolibException {
        File working = new File(appDir, folder);

        if (!working.exists()) {
            return;
        }

        File stored = new File(appDir, APK_DIRNAME + "/" + folder);
        if (apkOptions.forceBuildAll || isModified(working, stored)) {
            LOGGER.info("Copying libs... (/" + folder + ")");
            try {
                OS.rmdir(stored);
                OS.cpdir(working, stored);
            } catch (BrutException ex) {
                throw new AndrolibException(ex);
            }
        }
    }

    /**
     * 如果copyOriginalFiles 为true
     * 拷贝original 到build/apk
     *
     * @param appDir appDir
     * @throws AndrolibException 自定义异常
     */
    public void buildCopyOriginalFiles(File appDir)
        throws AndrolibException {
        if (apkOptions.copyOriginalFiles) {
            File originalDir = new File(appDir, "original");
            if (originalDir.exists()) {
                try {
                    LOGGER.info("Copy original files...");
                    Directory in = (new ExtFile(originalDir)).getDirectory();
                    if (in.containsFile("AndroidManifest.xml")) {
                        LOGGER.info("Copy AndroidManifest.xml...");
                        in.copyToDir(new File(appDir, APK_DIRNAME), "AndroidManifest.xml");
                    }
                    if (in.containsDir("META-INF")) {
                        LOGGER.info("Copy META-INF...");
                        in.copyToDir(new File(appDir, APK_DIRNAME), "META-INF");
                    }
                } catch (DirectoryException ex) {
                    throw new AndrolibException(ex);
                }
            }
        }
    }

    /**
     * 从apktool.yml里面的unknownFiles获取未知（未处理）文件，并拷贝
     *
     * @param appDir  appDir
     * @param outFile outFile
     * @param meta    meta
     * @throws AndrolibException 自定义异常
     */
    public void buildUnknownFiles(File appDir, File outFile, MetaInfo meta)
        throws AndrolibException {
        if (meta.unknownFiles != null) {
            LOGGER.info("Copying unknown files/dir...");

            Map<String, String> files = meta.unknownFiles;
            File tempFile = new File(outFile.getParent(), outFile.getName() + ".apktool_temp");
            boolean renamed = outFile.renameTo(tempFile);
            if (!renamed) {
                throw new AndrolibException("Unable to rename temporary file");
            }

            try (
                ZipFile inputFile = new ZipFile(tempFile);
                ZipOutputStream actualOutput = new ZipOutputStream(new FileOutputStream(outFile))
            ) {
                copyExistingFiles(inputFile, actualOutput);
                copyUnknownFiles(appDir, actualOutput, files);
            } catch (IOException | BrutException ex) {
                throw new AndrolibException(ex);
            }

            // Remove our temporary file.
            tempFile.delete();
        }
    }

    /**
     * 拷贝存在文件
     *
     * @param inputFile  inputFile
     * @param outputFile outputFile
     * @throws IOException IO异常
     */
    private void copyExistingFiles(ZipFile inputFile, ZipOutputStream outputFile) throws IOException {
        // First, copy the contents from the existing outFile:
        Enumeration<? extends ZipEntry> entries = inputFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = new ZipEntry(entries.nextElement());

            // We can't reuse the compressed size because it depends on compression sizes.
//            我们不能重用压缩大小，因为它取决于压缩大小。
            entry.setCompressedSize(-1);
            outputFile.putNextEntry(entry);

            // No need to create directory entries in the final apk
//            不需要在最终的apk中创建目录条目
            if (!entry.isDirectory()) {
                BrutIO.copy(inputFile, outputFile, entry);
            }

            outputFile.closeEntry();
        }
    }

    /**
     * 拷贝未知文件
     *
     * @param appDir     appDir
     * @param outputFile outputFile
     * @param files      files
     * @throws BrutException 自定义异常
     * @throws IOException   IO异常
     */
    private void copyUnknownFiles(File appDir, ZipOutputStream outputFile, Map<String, String> files)
        throws BrutException, IOException {
        File unknownFileDir = new File(appDir, UNK_DIRNAME);

        // loop through unknown files
        for (Map.Entry<String, String> unknownFileInfo : files.entrySet()) {
            File inputFile;

            try {
                inputFile = new File(unknownFileDir, BrutIO.sanitizeUnknownFile(unknownFileDir, unknownFileInfo.getKey()));
            } catch (RootUnknownFileException | InvalidUnknownFileException | TraversalUnknownFileException exception) {
                LOGGER.warning(String.format("Skipping file %s (%s)", unknownFileInfo.getKey(), exception.getMessage()));
                continue;
            }

            if (inputFile.isDirectory()) {
                continue;
            }

            ZipEntry newEntry = new ZipEntry(unknownFileInfo.getKey());
            int method = Integer.parseInt(unknownFileInfo.getValue());
            LOGGER.fine(String.format("Copying unknown file %s with method %d", unknownFileInfo.getKey(), method));
            if (method == ZipEntry.STORED) {
//                未压缩
                newEntry.setMethod(ZipEntry.STORED);
                newEntry.setSize(inputFile.length());
//                -1 表示未知
                newEntry.setCompressedSize(-1);
                BufferedInputStream unknownFile = new BufferedInputStream(new FileInputStream(inputFile));
//                CRC32冗余校验
                CRC32 crc = BrutIO.calculateCrc(unknownFile);
                newEntry.setCrc(crc.getValue());
            } else {
//                压缩
                newEntry.setMethod(ZipEntry.DEFLATED);
            }
            outputFile.putNextEntry(newEntry);

            BrutIO.copy(inputFile, outputFile);
            outputFile.closeEntry();
        }
    }

    /**
     * 构建APK
     *
     * @param appDir appDir 根目录
     * @param outApk outApk dis下的输出文件
     * @throws AndrolibException 自定义异常
     */
    public void buildApk(File appDir, File outApk) throws AndrolibException {
        LOGGER.info("Building apk file...");
        if (outApk.exists()) {
            outApk.delete();
        } else {
//            dis目录
            File outDir = outApk.getParentFile();
            if (outDir != null && !outDir.exists()) {
                outDir.mkdirs();
            }
        }
        File assetDir = new File(appDir, "assets");
        if (!assetDir.exists()) {
            assetDir = null;
        }
//        压缩成apk
        mAndRes.zipPackage(outApk, new File(appDir, APK_DIRNAME), assetDir);
    }

    /**
     * 公开框架资源
     *
     * @param arscFile arscFile
     * @throws AndrolibException 自定义异常
     */
    public void publicizeResources(File arscFile) throws AndrolibException {
        mAndRes.publicizeResources(arscFile);
    }

    /**
     * 安装框架
     *
     * @param frameFile frameFile
     * @throws AndrolibException 自定义异常
     */
    public void installFramework(File frameFile)
        throws AndrolibException {
        mAndRes.installFramework(frameFile);
    }

    /**
     * 框架list
     *
     * @throws AndrolibException 自定义异常
     */
    public void listFrameworks() throws AndrolibException {
        mAndRes.listFrameworkDirectory();
    }

    /**
     * 清空框架
     *
     * @throws AndrolibException 自定义异常
     */
    public void emptyFrameworkDirectory() throws AndrolibException {
        mAndRes.emptyFrameworkDirectory();
    }

    /**
     * 是否是框架apk
     *
     * @param resTable ResTable
     * @return boolean
     */
    public boolean isFrameworkApk(ResTable resTable) {
        for (ResPackage pkg : resTable.listMainPackages()) {
            if (pkg.getId() < 64) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取版本
     *
     * @return String
     */
    public static String getVersion() {
        return ApktoolProperties.get("application.version");
    }

    /**
     * 从使用的框架解析
     *
     * @param usesFramework usesFramework
     * @return File[]
     * @throws AndrolibException 自定义异常
     */
    private File[] parseUsesFramework(UsesFramework usesFramework)
        throws AndrolibException {
        if (usesFramework == null) {
            return null;
        }

        List<Integer> ids = usesFramework.ids;
        if (ids == null || ids.isEmpty()) {
            return null;
        }

//        框架tag
        String tag = usesFramework.tag;
        File[] files = new File[ids.size()];
        int i = 0;
        for (int id : ids) {
            files[i++] = mAndRes.getFrameworkApk(id, tag);
        }
        return files;
    }

    /**
     * 是否有修改
     *
     * @param working working
     * @param stored  stored
     * @return boolean
     */
    private boolean isModified(File working, File stored) {
        return !stored.exists() || BrutIO.recursiveModifiedTime(working) > BrutIO.recursiveModifiedTime(stored);
    }

    /**
     * 文件是否存在
     *
     * @param working working
     * @return boolean
     */
    private boolean isFile(File working) {
        return working.exists();
    }

    /**
     * 是否有修改
     *
     * @param working working
     * @param stored  stored
     * @return boolean
     */
    private boolean isModified(File[] working, File[] stored) {
        for (int i = 0; i < stored.length; i++) {
            if (!stored[i].exists()) {
                return true;
            }
        }
        return BrutIO.recursiveModifiedTime(working) > BrutIO.recursiveModifiedTime(stored);
    }

    /**
     * 批量生成新文件
     *
     * @param names names
     * @param dir   dir
     * @return File[]
     */
    private File[] newFiles(String[] names, File dir) {
        File[] files = new File[names.length];
        for (int i = 0; i < names.length; i++) {
            files[i] = new File(dir, names[i]);
        }
        return files;
    }

    public void close() throws IOException {
        mAndRes.close();
    }

    private final static Logger LOGGER = Logger.getLogger(Androlib.class.getName());

    /**
     * smali文件夹名
     */
    private final static String SMALI_DIRNAME = "smali";
    /**
     * build/apk文件夹名
     */
    private final static String APK_DIRNAME = "build/apk";
    /**
     * unknown文件夹名
     */
    private final static String UNK_DIRNAME = "unknown";
    /**
     * apk资源名
     */
    private final static String[] APK_RESOURCES_FILENAMES = new String[]{
        "resources.arsc", "AndroidManifest.xml", "res"};
    /**
     * 没有res文件名的Apk资源
     */
    private final static String[] APK_RESOURCES_WITHOUT_RES_FILENAMES = new String[]{
        "resources.arsc", "AndroidManifest.xml"};
    /**
     * app资源文件名
     */
    private final static String[] APP_RESOURCES_FILENAMES = new String[]{
        "AndroidManifest.xml", "res"};

    /**
     * 清单文件 AndroidManifest.xml
     */
    private final static String[] APK_MANIFEST_FILENAMES = new String[]{
        "AndroidManifest.xml"};
    /**
     * 标准APK的所有文件名
     */
    private final static String[] APK_STANDARD_ALL_FILENAMES = new String[]{
        "classes.dex", "AndroidManifest.xml", "resources.arsc", "res", "r", "R",
        "lib", "libs", "assets", "META-INF", "kotlin"};

    /**
     * 不压缩的文件
     */
    private final static Pattern NO_COMPRESS_PATTERN = Pattern.compile("(" +
        "jpg|jpeg|png|gif|wav|mp2|mp3|ogg|aac|mpg|mpeg|mid|midi|smf|jet|rtttl|imy|xmf|mp4|" +
        "m4a|m4v|3gp|3gpp|3g2|3gpp2|amr|awb|wma|wmv|webm|webp|mkv)$");
}
