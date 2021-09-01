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
package brut.apktool;

import brut.androlib.*;
import brut.androlib.err.CantFindFrameworkResException;
import brut.androlib.err.InFileNotFoundException;
import brut.androlib.err.OutDirExistsException;
import brut.common.BrutException;
import brut.directory.DirectoryException;
import brut.util.AaptManager;
import brut.util.OSDetection;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.*;

/**
 * 程序主入口， 主要的界面逻辑
 * <p>
 * brut.apktool.lib - （主要库代码）
 * brut.apktool.cli - 程序的 cli 界面
 * brut.j.dir - javaDir实用项目
 * brut.j.util - javaUtil实用项目
 * brut.j.common - javaCommon 实用项目
 */
public class Main {
    public static void main(String[] args) throws IOException, InterruptedException, BrutException {

        // headless
        // 开启了headless模式
        System.setProperty("java.awt.headless", "true");

        // set verbosity default
        // 设置为默认模式
        Verbosity verbosity = Verbosity.NORMAL;

        // cli parser
        // 命令行解释器
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine;

        // load options
        // 加载命令行选项
        _Options();

        try {
//           解析参数 allOptions:所有的选项, args:输入参, false:遇到无法识别的参数将停止
            commandLine = parser.parse(allOptions, args, false);

//            判断系统是否是64位
            if (!OSDetection.is64Bit()) {
                System.err.println("32 bit support is deprecated. Apktool will not support 32bit on v2.6.0.");
            }
        } catch (ParseException ex) {
            System.err.println(ex.getMessage());
//          使用说明（帮助）
            usage();
            System.exit(1);
            return;
        }

        // check for verbose / quiet
//        判断命令行模式是verbose还是quiet，默认为Verbosity.NORMAL
        if (commandLine.hasOption("-v") || commandLine.hasOption("--verbose")) {
            verbosity = Verbosity.VERBOSE;
        } else if (commandLine.hasOption("-q") || commandLine.hasOption("--quiet")) {
            verbosity = Verbosity.QUIET;
        }
//        加载日志模式
        setupLogging(verbosity);

        // check for advance mode
//        判断命令行是否advance模式
        if (commandLine.hasOption("advance") || commandLine.hasOption("advanced")) {
            setAdvanceMode();
        }

        boolean cmdFound = false;
//        遍历命令行输入参数，匹配参数忽略大小写
        for (String opt : commandLine.getArgs()) {
            if (opt.equalsIgnoreCase("d") || opt.equalsIgnoreCase("decode")) {
//                解包
                cmdDecode(commandLine);
                cmdFound = true;
            } else if (opt.equalsIgnoreCase("b") || opt.equalsIgnoreCase("build")) {
//                回编
                cmdBuild(commandLine);
                cmdFound = true;
            } else if (opt.equalsIgnoreCase("if") || opt.equalsIgnoreCase("install-framework")) {
//                安装框架
                cmdInstallFramework(commandLine);
                cmdFound = true;
            } else if (opt.equalsIgnoreCase("empty-framework-dir")) {
//                清空框架
                cmdEmptyFrameworkDirectory(commandLine);
                cmdFound = true;
            } else if (opt.equalsIgnoreCase("list-frameworks")) {
//                框架list
                cmdListFrameworks(commandLine);
                cmdFound = true;
            } else if (opt.equalsIgnoreCase("publicize-resources")) {
//                PublicizeResources
                cmdPublicizeResources(commandLine);
                cmdFound = true;
            }
        }

        // if no commands ran, run the version / usage check.
//        如果上诉遍历没有匹配， 判断是否是查询版本，否则 使用说明（帮助）
        if (!cmdFound) {
            if (commandLine.hasOption("version")) {
                _version();
                System.exit(0);
            } else {
                usage();
            }
        }
    }


    /**
     * 解包操作
     *
     * @param cli 命令行
     * @throws AndrolibException 自定义 AndrolibException 异常
     */
    private static void cmdDecode(CommandLine cli) throws AndrolibException {
        ApkDecoder decoder = new ApkDecoder();

//      参数个数
        int paraCount = cli.getArgList().size();
//      解包apk路径为最后一个参数
        String apkName = cli.getArgList().get(paraCount - 1);
        File outDir;

        // check for options
//        匹配参数
        if (cli.hasOption("s") || cli.hasOption("no-src")) {
//          不解码dex文件
            decoder.setDecodeSources(ApkDecoder.DECODE_SOURCES_NONE);
        }
        if (cli.hasOption("only-main-classes")) {
//          仅在根目录中分解 dex 类（classes[0-9].dex）
            decoder.setDecodeSources(ApkDecoder.DECODE_SOURCES_SMALI_ONLY_MAIN_CLASSES);
        }
        if (cli.hasOption("d") || cli.hasOption("debug")) {
//           在调试模式下解码。 已经被移除了详情： https://github.com/iBotPeaches/Apktool/issues/1061
            System.err.println("SmaliDebugging has been removed in 2.1.0 onward. Please see: https://github.com/iBotPeaches/Apktool/issues/1061");
            System.exit(1);
        }
        if (cli.hasOption("b") || cli.hasOption("no-debug-info")) {
//            防止baksmali输出调试信息(。Local， .param， .line等)。如果您正在比较来自不同版本的相同APK的smali，首选使用。行号和调试在不同版本之间会发生变化，这会使DIFF报告变得很麻烦。
            decoder.setBaksmaliDebugMode(false);
        }
        if (cli.hasOption("t") || cli.hasOption("frame-tag")) {
//           使用通过<TAG>标记的框架文件
            decoder.setFrameworkTag(cli.getOptionValue("t"));
        }
        if (cli.hasOption("f") || cli.hasOption("force")) {
//            强制删除目标目录。当试图解码到一个已经存在的文件夹时使用
            decoder.setForceDelete(true);
        }
        if (cli.hasOption("r") || cli.hasOption("no-res")) {
//            这将防止资源的反编译。这样就保留了资源。Arsc完整没有任何解码。如果只是编辑Java (smali)，那么这是更快的反编译和重建的建议操作
            decoder.setDecodeResources(ApkDecoder.DECODE_RESOURCES_NONE);
        }
        if (cli.hasOption("force-manifest")) {
//            强制AndroidManifest解码，不管资源标志是否解码。将更有可能阻止重建作为静态分析清单。
            decoder.setForceDecodeManifest(ApkDecoder.FORCE_DECODE_MANIFEST_FULL);
        }
        if (cli.hasOption("no-assets")) {
//            防止解码/复制未知资产文件。
            decoder.setDecodeAssets(ApkDecoder.DECODE_ASSETS_NONE);
        }
        if (cli.hasOption("k") || cli.hasOption("keep-broken-res")) {
//            Invalid Config Flags Detected. Dropping Resources...
//            如果出现类似“检测到无效配置标志”的错误。删除资源……”。这意味着APK具有Apktool所能处理的不同结构。这可能是一个较新的Android版本，或者是一个不符合标准的随机APK。运行这将允许解码，但然后你必须手动修复文件夹中的-ERR。
            decoder.setKeepBrokenResources(true);
        }
        if (cli.hasOption("p") || cli.hasOption("frame-path")) {
//          应该存储/读取框架文件的文件夹位置
            decoder.setFrameworkDir(cli.getOptionValue("p"));
        }
        if (cli.hasOption("m") || cli.hasOption("match-original")) {
//            匹配尽可能接近原始文件的文件，但防止重新生成。
            decoder.setAnalysisMode(true);
        }
        if (cli.hasOption("api") || cli.hasOption("api-level")) {
//            要构建的smali文件的数字api级别(默认为minSdkVersion)
            decoder.setApiLevel(Integer.parseInt(cli.getOptionValue("api")));
        }
        if (cli.hasOption("o") || cli.hasOption("output")) {
//            apk要输出的文件夹的名称
            outDir = new File(cli.getOptionValue("o"));
            decoder.setOutDir(outDir);
        } else {
            // make out folder manually using name of apk
//            使用apk的名称来创建文件夹
            String outName = apkName;
            outName = outName.endsWith(".apk") ? outName.substring(0,
                outName.length() - 4).trim() : outName + ".out";

            // make file from path
            outName = new File(outName).getName();
            outDir = new File(outName);
            decoder.setOutDir(outDir);
        }

//        设置待解包的APK
        decoder.setApkFile(new File(apkName));

        try {
//            解包
            decoder.decode();
        } catch (OutDirExistsException ex) {
            System.err
                .println("Destination directory ("
                    + outDir.getAbsolutePath()
                    + ") "
                    + "already exists. Use -f switch if you want to overwrite it.");
            System.exit(1);
        } catch (InFileNotFoundException ex) {
            System.err.println("Input file (" + apkName + ") " + "was not found or was not readable.");
            System.exit(1);
        } catch (CantFindFrameworkResException ex) {
            System.err
                .println("Can't find framework resources for package of id: "
                    + String.valueOf(ex.getPkgId())
                    + ". You must install proper "
                    + "framework files, see project website for more info.");
            System.exit(1);
        } catch (IOException ex) {
            System.err.println("Could not modify file. Please ensure you have permission.");
            System.exit(1);
        } catch (DirectoryException ex) {
            System.err.println("Could not modify internal dex files. Please ensure you have permission.");
            System.exit(1);
        } finally {
            try {
                decoder.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 回编操作
     *
     * @param cli 命令行
     * @throws BrutException 自定义BrutException异常
     */
    private static void cmdBuild(CommandLine cli) throws BrutException {
        String[] args = cli.getArgs();
        String appDirName = args.length < 2 ? "." : args[1];
        File outFile;
        ApkOptions apkOptions = new ApkOptions();

        // check for build options
        if (cli.hasOption("f") || cli.hasOption("force-all")) {
//            在生成过程中覆盖现有文件，重新组合资源。Arsc文件和dex文件
            apkOptions.forceBuildAll = true;
        }
        if (cli.hasOption("d") || cli.hasOption("debug")) {
//            添加debuggable="true"到AndroidManifest文件。
            apkOptions.debugMode = true;
        }
        if (cli.hasOption("v") || cli.hasOption("verbose")) {
//            verbose 详细输出
            apkOptions.verbose = true;
        }
        if (cli.hasOption("a") || cli.hasOption("aapt")) {
//            从指定的文件位置加载aapt，而不是依赖于路径。如果没有找到文件，则返回到$PATH加载。除非$PATH引用预构建的自定义aapt。这很可能行不通
            apkOptions.aaptPath = cli.getOptionValue("a");
        }
        if (cli.hasOption("c") || cli.hasOption("copy-original")) {
//            复制原始AndroidManifest.xml和META-INF文件夹到内置apk . 2.60移除
            System.err.println("-c/--copy-original has been deprecated. Removal planned for v2.6.0 (#2129)");
            apkOptions.copyOriginalFiles = true;
        }
        if (cli.hasOption("p") || cli.hasOption("frame-path")) {
//            加载框架文件的位置
            apkOptions.frameworkFolderLocation = cli.getOptionValue("p");
        }
        if (cli.hasOption("nc") || cli.hasOption("no-crunch")) {
//            在构建步骤中禁用资源文件的处理。
            apkOptions.noCrunch = true;
        }

        // Temporary flag to enable the use of aapt2. This will tranform in time to a use-aapt1 flag, which will be
        // legacy and eventually removed.
        if (cli.hasOption("use-aapt2")) {
//            使用aapt2二进制文件而不是appt
            apkOptions.useAapt2 = true;
        }
        if (cli.hasOption("api") || cli.hasOption("api-level")) {
//            要构建的smali文件的数字api级别(默认为minSdkVersion)
            apkOptions.forceApi = Integer.parseInt(cli.getOptionValue("api"));
        }
        if (cli.hasOption("o") || cli.hasOption("output")) {
//           apk要输出的路径
            outFile = new File(cli.getOptionValue("o"));
        } else {
            outFile = null;
        }

        // try and build apk
        try {
            if (cli.hasOption("a") || cli.hasOption("aapt")) {
//             从指定的文件位置加载aapt，而不是依赖于路径。如果没有找到文件，则返回到$PATH加载。除非$PATH引用预构建的自定义aapt。这很可能行不通
                apkOptions.aaptVersion = AaptManager.getAaptVersion(cli.getOptionValue("a"));
            }
            new Androlib(apkOptions).build(new File(appDirName), outFile);
        } catch (BrutException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }

    /**
     * 安装框架
     *
     * @param cli 命令行
     * @throws AndrolibException 自定义AndrolibException 异常
     */
    private static void cmdInstallFramework(CommandLine cli) throws AndrolibException {
        int paraCount = cli.getArgList().size();
        String apkName = cli.getArgList().get(paraCount - 1);

        ApkOptions apkOptions = new ApkOptions();
        if (cli.hasOption("p") || cli.hasOption("frame-path")) {
//           应该存储/读取框架文件的文件夹位置
            apkOptions.frameworkFolderLocation = cli.getOptionValue("p");
        }
        if (cli.hasOption("t") || cli.hasOption("tag")) {
//            使用标签框架
            apkOptions.frameworkTag = cli.getOptionValue("t");
        }
        new Androlib(apkOptions).installFramework(new File(apkName));
    }

    /**
     * 框架list
     *
     * @param cli 命令行
     * @throws AndrolibException 自定义 AndrolibException 异常
     */
    private static void cmdListFrameworks(CommandLine cli) throws AndrolibException {
        ApkOptions apkOptions = new ApkOptions();
        if (cli.hasOption("p") || cli.hasOption("frame-path")) {
//            应该存储/读取框架文件的文件夹位置
            apkOptions.frameworkFolderLocation = cli.getOptionValue("p");
        }

        new Androlib(apkOptions).listFrameworks();
    }

    /**
     * PublicizeResources
     *
     * @param cli 命令行
     * @throws AndrolibException 自定义 AndrolibException 异常
     */
    private static void cmdPublicizeResources(CommandLine cli) throws AndrolibException {
        int paraCount = cli.getArgList().size();
        String apkName = cli.getArgList().get(paraCount - 1);

        new Androlib().publicizeResources(new File(apkName));
    }

    /**
     * 清空框架文件
     *
     * @param cli 命令行
     * @throws AndrolibException 自定义 AndrolibException 异常
     */
    private static void cmdEmptyFrameworkDirectory(CommandLine cli) throws AndrolibException {
        ApkOptions apkOptions = new ApkOptions();

        if (cli.hasOption("f") || cli.hasOption("force")) {
//            强制删除
            apkOptions.forceDeleteFramework = true;
        }
        if (cli.hasOption("p") || cli.hasOption("frame-path")) {
//            框架路径
            apkOptions.frameworkFolderLocation = cli.getOptionValue("p");
        }

        new Androlib(apkOptions).emptyFrameworkDirectory();
    }

    /**
     * 版本
     */
    private static void _version() {
        System.out.println(Androlib.getVersion());
    }

    /**
     * 创建命令行选项
     */
    @SuppressWarnings("static-access")
    private static void _Options() {

        // create options
//        版本
        Option versionOption = Option.builder("version")
            .longOpt("version")
            .desc("prints the version then exits")
            .build();

//        advance模式 提前输出
        Option advanceOption = Option.builder("advance")
            .longOpt("advanced")
            .desc("prints advance information.")
            .build();

//          不解码dex文件
        Option noSrcOption = Option.builder("s")
            .longOpt("no-src")
            .desc("Do not decode sources.")
            .build();

//          仅在根目录中分解 dex 类（classes[0-9].dex）
        Option onlyMainClassesOption = Option.builder()
            .longOpt("only-main-classes")
            .desc("Only disassemble the main dex classes (classes[0-9]*.dex) in the root.")
            .build();

//          不解码asrc文件
        Option noResOption = Option.builder("r")
            .longOpt("no-res")
            .desc("Do not decode resources.")
            .build();

//         强制AndroidManifest解码，不管资源标志是否解码。将更有可能阻止重建作为静态分析清单。
        Option forceManOption = Option.builder()
            .longOpt("force-manifest")
            .desc("Decode the APK's compiled manifest, even if decoding of resources is set to \"false\".")
            .build();

//            防止解码/复制未知资产文件。
        Option noAssetOption = Option.builder()
            .longOpt("no-assets")
            .desc("Do not decode assets.")
            .build();

//        在调试模式下解码
        Option debugDecOption = Option.builder("d")
            .longOpt("debug")
            .desc("REMOVED (DOES NOT WORK): Decode in debug mode.")
            .build();

//        保持文件尽可能接近原始。防止重建
        Option analysisOption = Option.builder("m")
            .longOpt("match-original")
            .desc("Keeps files to closest to original as possible. Prevents rebuild.")
            .build();

//        要生成的文件的数字api级，例如ICS的14
        Option apiLevelOption = Option.builder("api")
            .longOpt("api-level")
            .desc("The numeric api-level of the file to generate, e.g. 14 for ICS.")
            .hasArg(true)
            .argName("API")
            .build();

//        在APK编译的清单中将android:debuggable设置为“true”
        Option debugBuiOption = Option.builder("d")
            .longOpt("debug")
            .desc("Sets android:debuggable to \"true\" in the APK's compiled manifest")
            .build();

//        不要写出调试信息(Local， .param， .line等)
        Option noDbgOption = Option.builder("b")
            .longOpt("no-debug-info")
            .desc("don't write out debug info (.local, .param, .line, etc.)")
            .build();

//        强制删除目标目录
        Option forceDecOption = Option.builder("f")
            .longOpt("force")
            .desc("Force delete destination directory.")
            .build();

//        使用标记为<tag>的框架文件
        Option frameTagOption = Option.builder("t")
            .longOpt("frame-tag")
            .desc("Uses framework files tagged by <tag>.")
            .hasArg(true)
            .argName("tag")
            .build();

//        使用位于<dir>的框架文件
        Option frameDirOption = Option.builder("p")
            .longOpt("frame-path")
            .desc("Uses framework files located in <dir>.")
            .hasArg(true)
            .argName("dir")
            .build();

//        将框架文件存储到<dir>
        Option frameIfDirOption = Option.builder("p")
            .longOpt("frame-path")
            .desc("Stores framework files into <dir>.")
            .hasArg(true)
            .argName("dir")
            .build();

//        如果出现错误和某些资源被删除，则使用。
//        检测到无效的配置标志。放弃资源\”，而你
//        无论如何都要解码它们，即使有错误。你必须
//        在构建之前手动修复它们。
        Option keepResOption = Option.builder("k")
            .longOpt("keep-broken-res")
            .desc("Use if there was an error and some resources were dropped, e.g.\n"
                + "            \"Invalid config flags detected. Dropping resources\", but you\n"
                + "            want to decode them anyway, even with errors. You will have to\n"
                + "            fix them manually before building.")
            .build();

//        跳过变更检测并构建所有文件
        Option forceBuiOption = Option.builder("f")
            .longOpt("force-all")
            .desc("Skip changes detection and build all files.")
            .build();

//        从规定的位置装载AAPT
        Option aaptOption = Option.builder("a")
            .longOpt("aapt")
            .hasArg(true)
            .argName("loc")
            .desc("Loads aapt from specified location.")
            .build();

//        升级apktool以使用实验性的aapt2二进制文件
        Option aapt2Option = Option.builder()
            .longOpt("use-aapt2")
            .desc("Upgrades apktool to use experimental aapt2 binary.")
            .build();

//        复制原始AndroidManifest.xml和META-INF。有关更多信息，请参阅项目页面
        Option originalOption = Option.builder("c")
            .longOpt("copy-original")
            .desc("Copies original AndroidManifest.xml and META-INF. See project page for more info.")
            .build();

//        在构建步骤中禁用资源文件的处理
        Option noCrunchOption = Option.builder("nc")
            .longOpt("no-crunch")
            .desc("Disable crunching of resource files during the build step.")
            .build();

//        使用< Tag >的标签框架。
        Option tagOption = Option.builder("t")
            .longOpt("tag")
            .desc("Tag frameworks using <tag>.")
            .hasArg(true)
            .argName("tag")
            .build();

//        apk的名字。默认是dist / name.apk
        Option outputBuiOption = Option.builder("o")
            .longOpt("output")
            .desc("The name of apk that gets written. Default is dist/name.apk")
            .hasArg(true)
            .argName("dir")
            .build();

//        要写入的文件夹的名称。默认是apk.out
        Option outputDecOption = Option.builder("o")
            .longOpt("output")
            .desc("The name of folder that gets written. Default is apk.out")
            .hasArg(true)
            .argName("dir")
            .build();

//        安静模式
        Option quietOption = Option.builder("q")
            .longOpt("quiet")
            .build();

//        详细模式
        Option verboseOption = Option.builder("v")
            .longOpt("verbose")
            .build();

        // check for advance mode
//        检查高级模式
        if (isAdvanceMode()) {
            DecodeOptions.addOption(noDbgOption);
            DecodeOptions.addOption(keepResOption);
            DecodeOptions.addOption(analysisOption);
            DecodeOptions.addOption(onlyMainClassesOption);
            DecodeOptions.addOption(apiLevelOption);
            DecodeOptions.addOption(noAssetOption);
            DecodeOptions.addOption(forceManOption);

            BuildOptions.addOption(apiLevelOption);
            BuildOptions.addOption(debugBuiOption);
            BuildOptions.addOption(aaptOption);
            BuildOptions.addOption(originalOption);
            BuildOptions.addOption(aapt2Option);
            BuildOptions.addOption(noCrunchOption);
        }

        // add global options
//        添加全局选项
        normalOptions.addOption(versionOption);
        normalOptions.addOption(advanceOption);

        // add basic decode options
//        添加基本解码选项
        DecodeOptions.addOption(frameTagOption);
        DecodeOptions.addOption(outputDecOption);
        DecodeOptions.addOption(frameDirOption);
        DecodeOptions.addOption(forceDecOption);
        DecodeOptions.addOption(noSrcOption);
        DecodeOptions.addOption(noResOption);

        // add basic build options
//        添加基本构建选项
        BuildOptions.addOption(outputBuiOption);
        BuildOptions.addOption(frameDirOption);
        BuildOptions.addOption(forceBuiOption);

        // add basic framework options
//        添加基本框架选项
        frameOptions.addOption(tagOption);
        frameOptions.addOption(frameIfDirOption);

        // add empty framework options
//        添加空框架选项
        emptyFrameworkOptions.addOption(forceDecOption);
        emptyFrameworkOptions.addOption(frameIfDirOption);

        // add list framework options
//        添加列表框架选项
        listFrameworkOptions.addOption(frameIfDirOption);

        // add all, loop existing cats then manually add advance
//        添加所有，遍历已有的，然后添加advance
        for (Object op : normalOptions.getOptions()) {
            allOptions.addOption((Option) op);
        }
        for (Object op : DecodeOptions.getOptions()) {
            allOptions.addOption((Option) op);
        }
        for (Object op : BuildOptions.getOptions()) {
            allOptions.addOption((Option) op);
        }
        for (Object op : frameOptions.getOptions()) {
            allOptions.addOption((Option) op);
        }
        allOptions.addOption(apiLevelOption);
        allOptions.addOption(analysisOption);
        allOptions.addOption(debugDecOption);
        allOptions.addOption(noDbgOption);
        allOptions.addOption(forceManOption);
        allOptions.addOption(noAssetOption);
        allOptions.addOption(keepResOption);
        allOptions.addOption(debugBuiOption);
        allOptions.addOption(aaptOption);
        allOptions.addOption(originalOption);
        allOptions.addOption(verboseOption);
        allOptions.addOption(quietOption);
        allOptions.addOption(aapt2Option);
        allOptions.addOption(noCrunchOption);
        allOptions.addOption(onlyMainClassesOption);
    }

    /**
     * Advance模式下 给与详细帮助
     *
     * @return String
     */
    private static String verbosityHelp() {
        if (isAdvanceMode()) {
            return "[-q|--quiet OR -v|--verbose] ";
        } else {
            return "";
        }
    }

    /**
     * 使用方法说明
     */
    private static void usage() {
        _Options();
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(120);

        // print out license info prior to formatter.
        System.out.println(
            "Apktool v" + Androlib.getVersion() + " - a tool for reengineering Android apk files\n" +
                "with smali v" + ApktoolProperties.get("smaliVersion") +
                " and baksmali v" + ApktoolProperties.get("baksmaliVersion") + "\n" +
                "Copyright 2010 Ryszard Wiśniewski <brut.alll@gmail.com>\n" +
                "Copyright 2010 Connor Tumbleson <connor.tumbleson@gmail.com>");
        if (isAdvanceMode()) {
            System.out.println("Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)\n");
        } else {
            System.out.println("");
        }

        // 4 usage outputs (general, frameworks, decode, build)
//        4个使用帮助输出  选项
        formatter.printHelp("apktool " + verbosityHelp(), normalOptions);
        formatter.printHelp("apktool " + verbosityHelp() + "if|install-framework [options] <framework.apk>", frameOptions);
        formatter.printHelp("apktool " + verbosityHelp() + "d[ecode] [options] <file_apk>", DecodeOptions);
        formatter.printHelp("apktool " + verbosityHelp() + "b[uild] [options] <app_path>", BuildOptions);
        if (isAdvanceMode()) {
//            先行模式多输出3个帮助
            formatter.printHelp("apktool " + verbosityHelp() + "publicize-resources <file_path>", emptyOptions);
            formatter.printHelp("apktool " + verbosityHelp() + "empty-framework-dir [options]", emptyFrameworkOptions);
            formatter.printHelp("apktool " + verbosityHelp() + "list-frameworks [options]", listFrameworkOptions);
            System.out.println("");
        } else {
            System.out.println("");
        }

        // print out more information
//        更多详细信息
        System.out.println(
            "For additional info, see: https://ibotpeaches.github.io/Apktool/ \n"
                + "For smali/baksmali info, see: https://github.com/JesusFreke/smali");
    }

    /**
     * 设置日志模式
     * 1、默认
     * 2、详细
     * 3、安静
     *
     * @param verbosity Verbosity
     */
    private static void setupLogging(final Verbosity verbosity) {
        Logger logger = Logger.getLogger("");
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }
        LogManager.getLogManager().reset();

        if (verbosity == Verbosity.QUIET) {
            return;
        }

        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (getFormatter() == null) {
                    setFormatter(new SimpleFormatter());
                }

                try {
                    String message = getFormatter().format(record);
                    if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                        System.err.write(message.getBytes());
                    } else {
                        if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                            System.out.write(message.getBytes());
                        } else {
                            if (verbosity == Verbosity.VERBOSE) {
                                System.out.write(message.getBytes());
                            }
                        }
                    }
                } catch (Exception exception) {
                    reportError(null, exception, ErrorManager.FORMAT_FAILURE);
                }
            }

            @Override
            public void close() throws SecurityException {
            }

            @Override
            public void flush() {
            }
        };

        logger.addHandler(handler);

        if (verbosity == Verbosity.VERBOSE) {
            handler.setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
        } else {
            handler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return record.getLevel().toString().charAt(0) + ": "
                        + record.getMessage()
                        + System.getProperty("line.separator");
                }
            });
        }
    }

    /**
     * 是否先行模式
     *
     * @return boolean
     */
    private static boolean isAdvanceMode() {
        return advanceMode;
    }

    /**
     * 设置先行模式
     */
    private static void setAdvanceMode() {
        Main.advanceMode = true;
    }

    /**
     * 日志版本
     */
    private enum Verbosity {
        NORMAL, VERBOSE, QUIET
    }

    private static boolean advanceMode = false;

    private final static Options normalOptions;
    private final static Options DecodeOptions;
    private final static Options BuildOptions;
    private final static Options frameOptions;
    private final static Options allOptions;
    private final static Options emptyOptions;
    private final static Options emptyFrameworkOptions;
    private final static Options listFrameworkOptions;

    static {
        //normal and advance usage output
        normalOptions = new Options();
        BuildOptions = new Options();
        DecodeOptions = new Options();
        frameOptions = new Options();
        allOptions = new Options();
        emptyOptions = new Options();
        emptyFrameworkOptions = new Options();
        listFrameworkOptions = new Options();
    }
}
