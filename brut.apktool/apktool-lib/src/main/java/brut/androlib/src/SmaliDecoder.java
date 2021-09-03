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
package brut.androlib.src;

import brut.androlib.AndrolibException;
import org.jf.baksmali.Baksmali;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.DexBackedOdexFile;
import org.jf.dexlib2.analysis.InlineMethodResolver;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.MultiDexContainer;

import java.io.File;
import java.io.IOException;

/**
 * dex解包成 smali
 */
public class SmaliDecoder {

    /**
     * 解包
     *
     * @param apkFile  待解包APK
     * @param outDir   输出文件夹
     * @param dexName  解包的dex文件
     * @param bakDeb   bak是否开启debug模式
     * @param apiLevel api等级
     * @return DexFile
     * @throws AndrolibException 自定义异常
     */
    public static DexFile decode(File apkFile, File outDir, String dexName, boolean bakDeb, int apiLevel)
        throws AndrolibException {
        return new SmaliDecoder(apkFile, outDir, dexName, bakDeb, apiLevel).decode();
    }

    private SmaliDecoder(File apkFile, File outDir, String dexName, boolean bakDeb, int apiLevel) {
        mApkFile = apkFile;
        mOutDir = outDir;
        mDexFile = dexName;
        mBakDeb = bakDeb;
        mApiLevel = apiLevel;
    }

    /**
     * 解包具体操作
     *
     * @return DexFile
     * @throws AndrolibException 自定义异常
     */
    private DexFile decode() throws AndrolibException {
        try {
            final BaksmaliOptions options = new BaksmaliOptions();

            // options
            options.deodex = false;
            options.implicitReferences = false;
//            参数登记（注册）
            options.parameterRegisters = true;
//             本地（局部）指令
            options.localsDirective = true;
//            连续标签
            options.sequentialLabels = true;
//            debug信息
            options.debugInfo = mBakDeb;
//             代码补偿（补位?）
            options.codeOffsets = false;
//              accessor注解
            options.accessorComments = false;
            options.registerInfo = 0;
//            内联分离
            options.inlineResolver = null;

            // set jobs automatically
//            设置自动工作
            int jobs = Runtime.getRuntime().availableProcessors();
            if (jobs > 6) {
                jobs = 6;
            }

            // create the container
//            创建容器
            MultiDexContainer<? extends DexBackedDexFile> container =
                DexFileFactory.loadDexContainer(mApkFile, mApiLevel > 0 ? Opcodes.forApi(mApiLevel) : null);
//            dex的入口
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry;
            DexBackedDexFile dexFile;

            // If we have 1 item, ignore the passed file. Pull the DexFile we need.
//            如果我们有一个项目，忽略传递的文件。调出我们需要的DexFile。
            if (container.getDexEntryNames().size() == 1) {
//                只有一个dex文件的情况
                dexEntry = container.getEntry(container.getDexEntryNames().get(0));
            } else {
                dexEntry = container.getEntry(mDexFile);
            }

            // Double check the passed param exists
//            再次检查传递的参数是否存在
            if (dexEntry == null) {
                dexEntry = container.getEntry(container.getDexEntryNames().get(0));
            }

//            断言，再次判断 dexEntry是否为null
            assert dexEntry != null;
//            获得dex文件
            dexFile = dexEntry.getDexFile();

//            判断是否是odex文件，即通过dex优化后生成的odex文件
            if (dexFile.supportsOptimizedOpcodes()) {
                throw new AndrolibException("Warning: You are disassembling an odex file without deodexing it.");
            }

            if (dexFile instanceof DexBackedOdexFile) {
//              分解代码内联版本
                options.inlineResolver =
                    InlineMethodResolver.createInlineMethodResolver(((DexBackedOdexFile) dexFile).getOdexVersion());
            }

//            调用Baksmali的分离dex为smali
            Baksmali.disassembleDexFile(dexFile, mOutDir, jobs, options);

            return dexFile;
        } catch (IOException ex) {
            throw new AndrolibException(ex);
        }
    }

    /**
     * 待解包APK
     */
    private final File mApkFile;
    /**
     * 输出文件夹
     */
    private final File mOutDir;
    /**
     * 解包的dex文件
     */
    private final String mDexFile;
    /**
     * 是否开启debug
     */
    private final boolean mBakDeb;
    /**
     * api等级
     */
    private final int mApiLevel;
}
