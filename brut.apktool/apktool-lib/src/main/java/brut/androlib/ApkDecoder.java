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

import brut.androlib.err.InFileNotFoundException;
import brut.androlib.err.OutDirExistsException;
import brut.androlib.err.UndefinedResObjectException;
import brut.androlib.meta.MetaInfo;
import brut.androlib.meta.PackageInfo;
import brut.androlib.meta.UsesFramework;
import brut.androlib.meta.VersionInfo;
import brut.androlib.res.AndrolibResources;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResTable;
import brut.directory.ExtFile;
import brut.androlib.res.xml.ResXmlPatcher;
import brut.common.BrutException;
import brut.directory.DirectoryException;
import brut.util.OS;
import com.google.common.base.Strings;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * 解包类，封装了解包的调用
 */
public class ApkDecoder {
    public ApkDecoder() {
        this(new Androlib());
    }

    public ApkDecoder(Androlib androlib) {
        mAndrolib = androlib;
    }

    public ApkDecoder(File apkFile) {
        this(apkFile, new Androlib());
    }

    public ApkDecoder(File apkFile, Androlib androlib) {
        mAndrolib = androlib;
        setApkFile(apkFile);
    }

    /**
     * 设置待解包APK
     *
     * @param apkFile File
     */
    public void setApkFile(File apkFile) {
        if (mApkFile != null) {
            try {
                mApkFile.close();
            } catch (IOException ignored) {
            }
        }

        mApkFile = new ExtFile(apkFile);
        mResTable = null;
    }

    /**
     * 设置输出文件夹
     *
     * @param outDir File
     */
    public void setOutDir(File outDir) {
        mOutDir = outDir;
    }

    /**
     * 解包调用
     *
     * @throws AndrolibException  自定义 AndrolibException 异常
     * @throws IOException        IO异常
     * @throws DirectoryException 自定义 DirectoryException 异常
     */
    public void decode() throws AndrolibException, IOException, DirectoryException {
        try {
            File outDir = getOutDir();
//            保持解包破碎， 即遇到不能解包的是否继续解包
            AndrolibResources.sKeepBroken = mKeepBrokenResources;

//            当强制删除目录为false，且目录存在时  抛出异常
            if (!mForceDelete && outDir.exists()) {
                throw new OutDirExistsException();
            }

//            输入文件找不到异常
            if (!mApkFile.isFile() || !mApkFile.canRead()) {
                throw new InFileNotFoundException();
            }

            try {
                OS.rmdir(outDir);
            } catch (BrutException ex) {
                throw new AndrolibException(ex);
            }
            outDir.mkdirs();

            LOGGER.info("Using Apktool " + Androlib.getVersion() + " on " + mApkFile.getName());

//            是否包含 resources.arsc
            if (hasResources()) {
//                解码 resources.arsc
                switch (mDecodeResources) {
                    case DECODE_RESOURCES_NONE:
//                       没有解码资源，直接拷贝 ResourcesRaw
                        mAndrolib.decodeResourcesRaw(mApkFile, outDir);
                        if (mForceDecodeManifest == FORCE_DECODE_MANIFEST_FULL) {
//                             强制AndroidManifest解码，不管资源标志是否解码。
                            // done after raw decoding of resources because copyToDir overwrites dest files
                            if (hasManifest()) {
                                mAndrolib.decodeManifestWithResources(mApkFile, outDir, getResTable());
                            }
                        }
                        break;
                    case DECODE_RESOURCES_FULL:
                        if (hasManifest()) {
//                            解码AndroidManifest.xml文件
                            mAndrolib.decodeManifestWithResources(mApkFile, outDir, getResTable());
                        }
//                        resources.arsc 资源
                        mAndrolib.decodeResourcesFull(mApkFile, outDir, getResTable());
                        break;
                }
            } else {
                // if there's no resources.arsc, decode the manifest without looking
                // up attribute references
                if (hasManifest()) {
                    if (mDecodeResources == DECODE_RESOURCES_FULL
                        || mForceDecodeManifest == FORCE_DECODE_MANIFEST_FULL) {
//                        解码 AndroidManifest
                        mAndrolib.decodeManifestFull(mApkFile, outDir, getResTable());
                    } else {
//                        直接拷贝 ResourcesRaw
                        mAndrolib.decodeManifestRaw(mApkFile, outDir);
                    }
                }
            }

//            是否包含classes.dex
            if (hasSources()) {
                switch (mDecodeSources) {
                    case DECODE_SOURCES_NONE:
//                        拷贝classes.dex
                        mAndrolib.decodeSourcesRaw(mApkFile, outDir, "classes.dex");
                        break;
                    case DECODE_SOURCES_SMALI:
                    case DECODE_SOURCES_SMALI_ONLY_MAIN_CLASSES:
//                        解码classes.dex
                        mAndrolib.decodeSourcesSmali(mApkFile, outDir, "classes.dex", mBakDeb, mApiLevel);
                        break;
                }
            }

//            是否包含多个 dex 文件
            if (hasMultipleSources()) {
                // foreach unknown dex file in root, lets disassemble it
                Set<String> files = mApkFile.getDirectory().getFiles(true);
                for (String file : files) {
                    if (file.endsWith(".dex")) {
                        if (!file.equalsIgnoreCase("classes.dex")) {
                            switch (mDecodeSources) {
                                case DECODE_SOURCES_NONE:
//                                    拷贝原始文件
                                    mAndrolib.decodeSourcesRaw(mApkFile, outDir, file);
                                    break;
                                case DECODE_SOURCES_SMALI:
//                                    解码dex文件
                                    mAndrolib.decodeSourcesSmali(mApkFile, outDir, file, mBakDeb, mApiLevel);
                                    break;
                                case DECODE_SOURCES_SMALI_ONLY_MAIN_CLASSES:
//                                    解码主要的dex文件
                                    if (file.startsWith("classes") && file.endsWith(".dex")) {
//                                        符合classes开头，.dex结尾的文件解码
                                        mAndrolib.decodeSourcesSmali(mApkFile, outDir, file, mBakDeb, mApiLevel);
                                    } else {
//                                        拷贝原始文件
                                        mAndrolib.decodeSourcesRaw(mApkFile, outDir, file);
                                    }
                                    break;
                            }
                        }
                    }
                }
            }

//            拷贝不用解码的文件，如assets,lib,libs,kotlin
            mAndrolib.decodeRawFiles(mApkFile, outDir, mDecodeAssets);
//            拷贝未知文件，未处理的文件
            mAndrolib.decodeUnknownFiles(mApkFile, outDir);
            mUncompressedFiles = new ArrayList<String>();
//            记录未压缩文件
            mAndrolib.recordUncompressedFiles(mApkFile, mUncompressedFiles);
//            拷贝OriginalFiles包含二进制AndroidManifest.xml ， META-INF
            mAndrolib.writeOriginalFiles(mApkFile, outDir);
//            生成apktool.yml
            writeMetaFile();
        } catch (Exception ex) {
            throw ex;
        } finally {
            try {
                mApkFile.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 设置解码dex模式：
     * DECODE_SOURCES_NONE = 0x0000;
     * DECODE_SOURCES_SMALI = 0x0001;
     * DECODE_SOURCES_SMALI_ONLY_MAIN_CLASSES = 0x0010;
     *
     * @param mode 模式
     * @throws AndrolibException 自定义异常
     */
    public void setDecodeSources(short mode) throws AndrolibException {
        if (mode != DECODE_SOURCES_NONE && mode != DECODE_SOURCES_SMALI && mode != DECODE_SOURCES_SMALI_ONLY_MAIN_CLASSES) {
            throw new AndrolibException("Invalid decode sources mode: " + mode);
        }
        mDecodeSources = mode;
    }

    /**
     * 设置解码resources.arsc模式
     * DECODE_RESOURCES_NONE = 0x0100;
     * DECODE_RESOURCES_FULL = 0x0101;
     *
     * @param mode 模式
     * @throws AndrolibException 自定义异常
     */
    public void setDecodeResources(short mode) throws AndrolibException {
        if (mode != DECODE_RESOURCES_NONE && mode != DECODE_RESOURCES_FULL) {
            throw new AndrolibException("Invalid decode resources mode");
        }
        mDecodeResources = mode;
    }

    /**
     * 设置强制解码AndroidManifest.xml
     * FORCE_DECODE_MANIFEST_NONE = 0x0000;
     * FORCE_DECODE_MANIFEST_FULL = 0x0001;
     *
     * @param mode 模式
     * @throws AndrolibException 自定义异常
     */
    public void setForceDecodeManifest(short mode) throws AndrolibException {
        if (mode != FORCE_DECODE_MANIFEST_NONE && mode != FORCE_DECODE_MANIFEST_FULL) {
            throw new AndrolibException("Invalid force decode manifest mode");
        }
        mForceDecodeManifest = mode;
    }

    /**
     * 设置解码Assets
     * DECODE_ASSETS_NONE = 0x0000;
     * DECODE_ASSETS_FULL = 0x0001;
     *
     * @param mode 模式
     * @throws AndrolibException 自定义异常
     */
    public void setDecodeAssets(short mode) throws AndrolibException {
        if (mode != DECODE_ASSETS_NONE && mode != DECODE_ASSETS_FULL) {
            throw new AndrolibException("Invalid decode asset mode");
        }
        mDecodeAssets = mode;
    }

    /**
     * 设置分析模式,
     * 尽可能接近原始文件的文件，但防止重新生成。
     *
     * @param mode boolean
     */
    public void setAnalysisMode(boolean mode) {
        mAnalysisMode = mode;

        if (mResTable != null) {
            mResTable.setAnalysisMode(mode);
        }
    }

    /**
     * 设置smali文件的数字api级别
     *
     * @param apiLevel int
     */
    public void setApiLevel(int apiLevel) {
        mApiLevel = apiLevel;
    }

    /**
     * 当bakDeb 未false时防止baksmali输出调试信息(。Local， .param， .line等)
     *
     * @param bakDeb boolean
     */
    public void setBaksmaliDebugMode(boolean bakDeb) {
        mBakDeb = bakDeb;
    }

    /**
     * 强制删除目标目录
     *
     * @param forceDelete boolean
     */
    public void setForceDelete(boolean forceDelete) {
        mForceDelete = forceDelete;
    }

    /**
     * 设置目标框架文件
     *
     * @param tag String
     */
    public void setFrameworkTag(String tag) {
        mAndrolib.apkOptions.frameworkTag = tag;
    }

    /**
     * 设置遇到不能识别的内容（不符合标准的APK），仍然继续解码
     *
     * @param keepBrokenResources boolean
     */
    public void setKeepBrokenResources(boolean keepBrokenResources) {
        mKeepBrokenResources = keepBrokenResources;
    }

    /**
     * 设置框架文件位置
     *
     * @param dir String
     */
    public void setFrameworkDir(String dir) {
        mAndrolib.apkOptions.frameworkFolderLocation = dir;
    }

    /**
     * 获取ResTable
     *
     * @return ResTable
     * @throws AndrolibException 自定义异常
     */
    public ResTable getResTable() throws AndrolibException {
        if (mResTable == null) {
            boolean hasResources = hasResources();
            boolean hasManifest = hasManifest();
            if (!(hasManifest || hasResources)) {
                throw new AndrolibException(
                    "Apk doesn't contain either AndroidManifest.xml file or resources.arsc file");
            }
            mResTable = mAndrolib.getResTable(mApkFile, hasResources);
            mResTable.setAnalysisMode(mAnalysisMode);
        }
        return mResTable;
    }

    /**
     * 是否有classes.dex文件
     *
     * @return boolean
     * @throws AndrolibException 自定义异常
     */
    public boolean hasSources() throws AndrolibException {
        try {
            return mApkFile.getDirectory().containsFile("classes.dex");
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 是否有多个 .dex文件
     *
     * @return boolean
     * @throws AndrolibException 自定义异常
     */
    public boolean hasMultipleSources() throws AndrolibException {
        try {
            Set<String> files = mApkFile.getDirectory().getFiles(false);
            for (String file : files) {
                if (file.endsWith(".dex")) {
                    if (!file.equalsIgnoreCase("classes.dex")) {
                        return true;
                    }
                }
            }

            return false;
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 是否有AndroidManifest.xml
     *
     * @return boolean
     * @throws AndrolibException 自定义异常
     */
    public boolean hasManifest() throws AndrolibException {
        try {
            return mApkFile.getDirectory().containsFile("AndroidManifest.xml");
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 是否有resources.arsc
     *
     * @return boolean
     * @throws AndrolibException 自定义异常
     */
    public boolean hasResources() throws AndrolibException {
        try {
            return mApkFile.getDirectory().containsFile("resources.arsc");
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    public void close() throws IOException {
        if (mAndrolib != null) {
            mAndrolib.close();
        }
    }

    /**
     * 自定义解码dex模式标记
     */
    public final static short DECODE_SOURCES_NONE = 0x0000;
    public final static short DECODE_SOURCES_SMALI = 0x0001;
    public final static short DECODE_SOURCES_SMALI_ONLY_MAIN_CLASSES = 0x0010;

    /**
     * 自定义解码resources.arsc模式标记
     */
    public final static short DECODE_RESOURCES_NONE = 0x0100;
    public final static short DECODE_RESOURCES_FULL = 0x0101;

    /**
     * 自定义解码AndroidManifest.xml模式标记
     */
    public final static short FORCE_DECODE_MANIFEST_NONE = 0x0000;
    public final static short FORCE_DECODE_MANIFEST_FULL = 0x0001;

    /**
     * 自定义解码Assets模式标记
     */
    public final static short DECODE_ASSETS_NONE = 0x0000;
    public final static short DECODE_ASSETS_FULL = 0x0001;

    /**
     * 获得输出文件夹
     *
     * @return File
     * @throws AndrolibException 自定义异常
     */
    private File getOutDir() throws AndrolibException {
        if (mOutDir == null) {
            throw new AndrolibException("Out dir not set");
        }
        return mOutDir;
    }

    /**
     * 记录相关信息到MetaInfo
     * 生成apktool.yml
     *
     * @throws AndrolibException 自定义异常
     */
    private void writeMetaFile() throws AndrolibException {
        MetaInfo meta = new MetaInfo();
        meta.version = Androlib.getVersion();
        meta.apkFileName = mApkFile.getName();

        if (mResTable != null) {
            meta.isFrameworkApk = mAndrolib.isFrameworkApk(mResTable);
            putUsesFramework(meta);
            putSdkInfo(meta);
            putPackageInfo(meta);
            putVersionInfo(meta);
            putSharedLibraryInfo(meta);
            putSparseResourcesInfo(meta);
        } else {
            putMinSdkInfo(meta);
        }
        putUnknownInfo(meta);
        putFileCompressionInfo(meta);

        mAndrolib.writeMetaFile(mOutDir, meta);
    }

    /**
     * 记录使用框架信息
     *
     * @param meta MetaInfo
     */
    private void putUsesFramework(MetaInfo meta) {
        Set<ResPackage> pkgs = mResTable.listFramePackages();
        if (pkgs.isEmpty()) {
            return;
        }

        Integer[] ids = new Integer[pkgs.size()];
        int i = 0;
        for (ResPackage pkg : pkgs) {
            ids[i++] = pkg.getId();
        }
        Arrays.sort(ids);

        meta.usesFramework = new UsesFramework();
        meta.usesFramework.ids = Arrays.asList(ids);

        if (mAndrolib.apkOptions.frameworkTag != null) {
            meta.usesFramework.tag = mAndrolib.apkOptions.frameworkTag;
        }
    }

    /**
     * 记录Sdk版本信息
     *
     * @param meta MetaInfo
     */
    private void putSdkInfo(MetaInfo meta) {
        Map<String, String> info = mResTable.getSdkInfo();
        if (info.size() > 0) {
            String refValue;
            if (info.get("minSdkVersion") != null) {
                refValue = ResXmlPatcher.pullValueFromIntegers(mOutDir, info.get("minSdkVersion"));
                if (refValue != null) {
                    info.put("minSdkVersion", refValue);
                }
            }
            if (info.get("targetSdkVersion") != null) {
                refValue = ResXmlPatcher.pullValueFromIntegers(mOutDir, info.get("targetSdkVersion"));
                if (refValue != null) {
                    info.put("targetSdkVersion", refValue);
                }
            }
            if (info.get("maxSdkVersion") != null) {
                refValue = ResXmlPatcher.pullValueFromIntegers(mOutDir, info.get("maxSdkVersion"));
                if (refValue != null) {
                    info.put("maxSdkVersion", refValue);
                }
            }
            meta.sdkInfo = info;
        }
    }

    /**
     * 记录最低的SDK版本信息
     *
     * @param meta MetaInfo
     */
    private void putMinSdkInfo(MetaInfo meta) {
        int minSdkVersion = mAndrolib.getMinSdkVersion();
        if (minSdkVersion > 0) {
            Map<String, String> sdkInfo = new LinkedHashMap<>();
            sdkInfo.put("minSdkVersion", Integer.toString(minSdkVersion));
            meta.sdkInfo = sdkInfo;
        }
    }

    /**
     * 记录包相关信息
     *
     * @param meta MetaInfo
     */
    private void putPackageInfo(MetaInfo meta) throws AndrolibException {
        String renamed = mResTable.getPackageRenamed();
        String original = mResTable.getPackageOriginal();

        int id = mResTable.getPackageId();
        try {
            id = mResTable.getPackage(renamed).getId();
        } catch (UndefinedResObjectException ignored) {
        }

        if (Strings.isNullOrEmpty(original)) {
            return;
        }

        meta.packageInfo = new PackageInfo();

        // only put rename-manifest-package into apktool.yml, if the change will be required
        if (!renamed.equalsIgnoreCase(original)) {
            meta.packageInfo.renameManifestPackage = renamed;
        }
        meta.packageInfo.forcedPackageId = String.valueOf(id);
    }

    /**
     * 记录版本信息
     *
     * @param meta MetaInfo
     */
    private void putVersionInfo(MetaInfo meta) {
        VersionInfo info = mResTable.getVersionInfo();
        String refValue = ResXmlPatcher.pullValueFromStrings(mOutDir, info.versionName);
        if (refValue != null) {
            info.versionName = refValue;
        }
        meta.versionInfo = info;
    }

    /**
     * 记录共享库信息
     *
     * @param meta MetaInfo
     */
    private void putSharedLibraryInfo(MetaInfo meta) {
        meta.sharedLibrary = mResTable.getSharedLibrary();
    }

    /**
     * 记录SparseResources信息
     *
     * @param meta MetaInfo
     */
    private void putSparseResourcesInfo(MetaInfo meta) {
        meta.sparseResources = mResTable.getSparseResources();
    }

    /**
     * 记录未知文件信息
     *
     * @param meta MetaInfo
     */
    private void putUnknownInfo(MetaInfo meta) {
        meta.unknownFiles = mAndrolib.mResUnknownFiles.getUnknownFiles();
    }

    /**
     * 记录不压缩文件信息
     *
     * @param meta MetaInfo
     */
    private void putFileCompressionInfo(MetaInfo meta) {
        if (mUncompressedFiles != null && !mUncompressedFiles.isEmpty()) {
            meta.doNotCompress = mUncompressedFiles;
        }
    }

    /**
     * 解包核心
     */
    private final Androlib mAndrolib;

    /**
     * 日志
     */
    private final static Logger LOGGER = Logger.getLogger(Androlib.class.getName());

    /**
     * 待解包APK
     */
    private ExtFile mApkFile;
    /**
     * 输出文件夹
     */
    private File mOutDir;

    /**
     * Resource.arsc 的 ResTable
     */
    private ResTable mResTable;
    /**
     * 默认的解码.dex 模式
     */
    private short mDecodeSources = DECODE_SOURCES_SMALI;
    /**
     * 默认的解码 Resource.arsc 模式
     */
    private short mDecodeResources = DECODE_RESOURCES_FULL;
    /**
     * 默认的解码 AndroidManifest.xml 模式
     */
    private short mForceDecodeManifest = FORCE_DECODE_MANIFEST_NONE;
    /**
     * 默认的解码 Assets 模式
     */
    private short mDecodeAssets = DECODE_ASSETS_FULL;
    /**
     * 默认的强制删除文件夹
     */
    private boolean mForceDelete = false;
    /**
     * 默认的是否强制解包（遇到未知是否继续）
     */
    private boolean mKeepBrokenResources = false;
    /**
     * bak解包smali是否输出日志
     */
    private boolean mBakDeb = true;
    /**
     * 收集不压缩文件
     */
    private Collection<String> mUncompressedFiles;
    /**
     * 默认非分析模式
     */
    private boolean mAnalysisMode = false;
    /**
     * smali文件的数字api级别
     * 为0时默认为minSdkVersion
     */
    private int mApiLevel = 0;
}
