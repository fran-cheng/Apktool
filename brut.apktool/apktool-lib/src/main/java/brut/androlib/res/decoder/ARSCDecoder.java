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
package brut.androlib.res.decoder;

import android.util.TypedValue;
import brut.androlib.AndrolibException;
import brut.androlib.res.data.*;
import brut.androlib.res.data.value.*;
import brut.util.Duo;
import brut.util.ExtDataInput;
import com.google.common.io.LittleEndianDataInputStream;
import org.apache.commons.io.input.CountingInputStream;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * 解析resource.arsc 文件
 */
public class ARSCDecoder {
    /**
     * 解码 resource.arsc 用于安装框架或者公共资源
     *
     * @param arscStream       resource.arsc 文件输入流
     * @param findFlagsOffsets 是否找FlagsOffsets ===> true
     * @param keepBroken       是否保持破碎 ===> true
     * @return ARSCData
     * @throws AndrolibException 自定义异常
     */
    public static ARSCData decode(InputStream arscStream, boolean findFlagsOffsets, boolean keepBroken)
        throws AndrolibException {
        return decode(arscStream, findFlagsOffsets, keepBroken, new ResTable());
    }

    /**
     * 解码 resource.arsc
     *
     * @param arscStream       resource.arsc 文件输入流
     * @param findFlagsOffsets 是否找FlagsOffsets
     * @param keepBroken       是否保持破碎
     * @param resTable         ResTable
     * @return ARSCData
     * @throws AndrolibException 自定义异常
     */
    public static ARSCData decode(InputStream arscStream, boolean findFlagsOffsets, boolean keepBroken,
                                  ResTable resTable)
        throws AndrolibException {
        try {
            ARSCDecoder decoder = new ARSCDecoder(arscStream, resTable, findFlagsOffsets, keepBroken);
//            解码结果
            ResPackage[] pkgs = decoder.readTableHeader();
            return new ARSCData(pkgs, decoder.mFlagsOffsets == null
                ? null
                : decoder.mFlagsOffsets.toArray(new FlagsOffset[0]), resTable);
        } catch (IOException ex) {
            throw new AndrolibException("Could not decode arsc file", ex);
        }
    }

    /**
     * 构造方法
     *
     * @param arscStream        resource.arsc 文件输入流
     * @param resTable          ResTable
     * @param storeFlagsOffsets 是否找FlagsOffsets
     * @param keepBroken        是否保持破碎
     */
    private ARSCDecoder(InputStream arscStream, ResTable resTable, boolean storeFlagsOffsets, boolean keepBroken) {
        arscStream = mCountIn = new CountingInputStream(arscStream);
        if (storeFlagsOffsets) {
            mFlagsOffsets = new ArrayList<FlagsOffset>();
        } else {
            mFlagsOffsets = null;
        }
        // We need to explicitly cast to DataInput as otherwise the constructor is ambiguous.
        // We choose DataInput instead of InputStream as ExtDataInput wraps an InputStream in
        // a DataInputStream which is big-endian and ignores the little-endian behavior.
//        需要显式地转换为DataInput，否则构造函数是二义性的。
//        我们选择DataInput而不是InputStream，因为ExtDataInput封装了一个InputStream
//        DataInputStream是大端的，忽略小端行为。
        mIn = new ExtDataInput((DataInput) new LittleEndianDataInputStream(arscStream));
        mResTable = resTable;
        mKeepBroken = keepBroken;
    }

    /**
     * 获取resource.arsc 文件的table 头（整个文件的头）
     *
     * @return ResPackage
     * @throws IOException       IO异常
     * @throws AndrolibException 自定义异常
     */
    private ResPackage[] readTableHeader() throws IOException, AndrolibException {
//        移动指针到RES_TABLE_TYPE块 头的尾处
        nextChunkCheckType(Header.TYPE_TABLE);
//        1111 ==> 4位，等于16 即 F
//        读取4个字节，文本读取, 由于是16进制 具体原因查看 resource.arsc文件结构
//        4字节 8byte = 32位  ，16进制,二进制"4位"代表一个16进制的“位"
//        package数
        int packageCount = mIn.readInt();

//        读取整个字符串块
        mTableStrings = StringBlock.read(mIn);

//        创建package集
        ResPackage[] packages = new ResPackage[packageCount];

//        指针到下一块的头后面
        nextChunk();
        for (int i = 0; i < packageCount; i++) {
            mTypeIdOffset = 0;
//            读取package，赋值给package集 具体解析
            packages[i] = readTablePackage();
        }
        return packages;
    }

    /**
     * 解析table里面的package
     *
     * @return ResPackage
     * @throws IOException       IO异常
     * @throws AndrolibException 自定义异常
     */
    private ResPackage readTablePackage() throws IOException, AndrolibException {
//        检查当前是否在 RES_TABLE_PACKAGE_TYPE
        checkChunkType(Header.TYPE_PACKAGE);
//        读取Package IDs，  对于解包APK来说大部分情况下是 0x7F
        int id = mIn.readInt();

        if (id == 0) {
            // This means we are dealing with a Library Package, we should just temporarily
            // set the packageId to the next available id . This will be set at runtime regardless, but
            // for Apktool's use we need a non-zero packageId.
            // AOSP indicates 0x02 is next, as 0x01 is system and 0x7F is private.
//            这意味着我们正在处理一个库包，我们应该只是临时的
//            将packageId设置为下一个可用id。这将在运行时设置，但是
//            为了使用Apktool，我们需要一个非零的packageId。
//            AOSP表示0x02是下一个，因为0x01是系统，0x7F是私有的。
            id = 2;
            if (mResTable.getPackageOriginal() == null && mResTable.getPackageRenamed() == null) {
//                表示当前是一个共享库包
                mResTable.setSharedLibrary(true);
            }
        }

//        读取这个包的实际名称，（包名）
        String name = mIn.readNullEndedString(128, true);
        /* typeStrings */
//        定义资源“类型”符号表的ResStringPool_header的偏移量
        mIn.skipInt();
        /* lastPublicType */
//        typestring的最后一个索引，供其他人使用
        mIn.skipInt();
        /* keyStrings */
//        定义资源“键”符号表的ResStringPool_header的偏移量
        mIn.skipInt();
        /* lastPublicKey */
//        keystring中供其他人使用的最后一个索引
        mIn.skipInt();

        // TypeIdOffset was added platform_frameworks_base/@f90f2f8dc36e7243b85e0b6a7fd5a590893c827e
        // which is only in split/new applications.
//       添加了platform_frameworks_base/@f90f2f8dc36e7243b85e0b6a7fd5a590893c827e
//       这只是在拆分/新应用程序。
//       一般情况下，默认 splitHeaderSize = （2 + 2 + 4 + 4 + ( 2 * 128) + (4 * 4)）
        int splitHeaderSize = (2 + 2 + 4 + 4 + (2 * 128) + (4 * 5)); // short, short, int, int, char[128], int * 4
        if (mHeader.headerSize == splitHeaderSize) {
//            比正常的多读4字节 即上述分析的 4 * 4 和 4 * 5 的区别
            mTypeIdOffset = mIn.readInt();
        }

        if (mTypeIdOffset > 0) {
            LOGGER.warning("Please report this application to Apktool for a fix: https://github.com/iBotPeaches/Apktool/issues/1728");
        }

//       从流中读取字符串块 ， package下的ResStringPool_typeStrings
        mTypeNames = StringBlock.read(mIn);
//       从流中读取字符串块， package下的ResStringPool_keyStrings
        mSpecNames = StringBlock.read(mIn);

//        一般情况下是0x7f ==> 0111 1111  左移24位 ===> 0111 1111 0000 0000 0000 0000 0000 0000  ====>0x7f00 0000
//        即拿到 16 机制的 ResId 的值 0x7f00 0000
        mResId = id << 24;
//        将resource.arsc  ,0x7f ,包名  创建ResPackage
        mPkg = new ResPackage(mResTable, id, name);

//       跳转到下一块头部分
        nextChunk();
//        开始准备解析ResTable_type
        boolean flag = true;
        while (flag) {
            switch (mHeader.type) {
                case Header.TYPE_LIBRARY:
//                    解析ResTable_typeLibrary 共享库
                    readLibraryType();
                    break;
                case Header.TYPE_SPEC_TYPE:
//                    解析ResTable_typeSpec
                    readTableTypeSpec();
                    break;
                default:
                    flag = false;
                    break;
            }
        }

//        解析所有ResTable_type完成
        return mPkg;
    }

    /**
     * 读取库类型
     *
     * @throws AndrolibException 自定义异常
     * @throws IOException       IO异常
     */
    private void readLibraryType() throws AndrolibException, IOException {
//        检测位置
        checkChunkType(Header.TYPE_LIBRARY);
        int libraryCount = mIn.readInt();

        int packageId;
        String packageName;

        for (int i = 0; i < libraryCount; i++) {
            packageId = mIn.readInt();
            packageName = mIn.readNullEndedString(128, true);
            LOGGER.info(String.format("Decoding Shared Library (%s), pkgId: %d", packageName, packageId));
        }


        while (nextChunk().type == Header.TYPE_TYPE) {
            //        移动到下一块的头，是TYPE_TYPE继续读取
            readTableTypeSpec();
        }
    }

    /**
     * 解析ResTable_typeSpec
     *
     * @throws AndrolibException 自定义异常
     * @throws IOException       IO异常
     */
    private void readTableTypeSpec() throws AndrolibException, IOException {
//////        获取TypeSpec
        mTypeSpec = readSingleTableTypeSpec();
//        添加 TypeSpec 到 mResTypeSpecs
        addTypeSpec(mTypeSpec);

//      获取下一块的类型
        int type = nextChunk().type;
        ResTypeSpec resTypeSpec;

        while (type == Header.TYPE_SPEC_TYPE) {
//            ResTable_typeSpec
            resTypeSpec = readSingleTableTypeSpec();
//            添加 TypeSpec 到 mResTypeSpecs
            addTypeSpec(resTypeSpec);
//            获取下一块的类型
            type = nextChunk().type;

            // We've detected sparse resources, lets record this so we can rebuild in that same format (sparse/not)
            // with aapt2. aapt1 will ignore this.
//           我们已经检测到sparse resources，让我们记录这个，以便我们可以用aapt2以相同的格式(sparse/not)重建。Aapt1将忽略这一点。
            if (!mResTable.getSparseResources()) {
//                稀有资源
                mResTable.setSparseResources(true);
            }
        }

        while (type == Header.TYPE_TYPE) {
//             ResTable_type
            readTableType();

            // skip "TYPE 8 chunks" and/or padding data at the end of this chunk
//            跳过“TYPE 8 chunk”和/或在该chunk的末尾填充数据
            if (mCountIn.getCount() < mHeader.endPosition) {
//                当前指针位置在头的末尾前,不符合预期，日志警告，并跳到尾部
                LOGGER.warning("Unknown data detected. Skipping: " + (mHeader.endPosition - mCountIn.getCount()) + " byte(s)");
                mCountIn.skip(mHeader.endPosition - mCountIn.getCount());
            }

//            移动到下一个块的头部末尾
            type = nextChunk().type;

//            添加未读值， 未知无法读取 为@null引用
            addMissingResSpecs();
        }
    }

    /**
     * 读取当前块的ResTable_typeSpec （类型）
     * 将ResTypeSpec添加到mPkg
     * 并返回
     *
     * @return ResTypeSpec  ResTypeSpec
     * @throws AndrolibException 自定义异常
     * @throws IOException       IO异常
     */
    private ResTypeSpec readSingleTableTypeSpec() throws AndrolibException, IOException {
//        判断是否在ResTable_typeSpec
        checkChunkType(Header.TYPE_SPEC_TYPE);

//        读取此块所持有的类型标识符
        int id = mIn.readUnsignedByte();
//        跳过char res0，short res1
        mIn.skipBytes(3);
//        接下来的uint32_t条目配置掩码的数量
        int entryCount = mIn.readInt();

        if (mFlagsOffsets != null) {
//           添加偏移量标记
            mFlagsOffsets.add(new FlagsOffset(mCountIn.getCount(), entryCount));
        }

        /* flags */
//        跳过configmask
        mIn.skipBytes(entryCount * 4);
//        生成ResTable_typeSpec
        mTypeSpec = new ResTypeSpec(mTypeNames.getString(id - 1), mResTable, mPkg, id, entryCount);
//        将类型添加到mPkg
        mPkg.addType(mTypeSpec);
        return mTypeSpec;
    }

    /**
     * 读取ResTable_type
     *
     * @return ResType
     * @throws IOException       IO异常
     * @throws AndrolibException 自定义异常
     */
    private ResType readTableType() throws IOException, AndrolibException {
//        检查是否在ResTable_type块
        checkChunkType(Header.TYPE_TYPE);

//         此块所持有的类型标识符 -  类型ID的偏移量  ===> 类型ID
        int typeId = mIn.readUnsignedByte() - mTypeIdOffset;
//        判断是否已经获取了该类型
        if (mResTypeSpecs.containsKey(typeId)) {
//            计算资源ID值
            mResId = (0xff000000 & mResId) | mResTypeSpecs.get(typeId).getId() << 16;
            mTypeSpec = mResTypeSpecs.get(typeId);
        }

//        res0
        int typeFlags = mIn.readByte();
        /* reserved */
//        res1
        mIn.skipBytes(2);
//        后面的条目索引的数量
        int entryCount = mIn.readInt();
//        从ResTable_entry数据开始的头文件的偏移量
        int entriesStart = mIn.readInt();
//        记录ResTable_type下的Res_value，是否有读取到值
        mMissingResSpecs = new boolean[entryCount];
        Arrays.fill(mMissingResSpecs, true);
//      读取ResTable_config 块
        ResConfigFlags flags = readConfigFlags();
//     (mHeader.startPosition + entriesStart) ==> ResTable_map_entry 的开始位置   (entryCount * 4)===> entryOffsets 的大小
//        即entryOffsets的开始
        int position = (mHeader.startPosition + entriesStart) - (entryCount * 4);

        // For some APKs there is a disconnect between the reported size of Configs
        // If we find a mismatch skip those bytes.
//        对于一些apk，在Configs的报告大小之间存在断开 如果发现不匹配，跳过这些字节。
        if (position != mCountIn.getCount()) {
//            ResTable_config 块 的尾部 不是entryOffsets的开始
            LOGGER.warning("Invalid data detected. Skipping: " + (position - mCountIn.getCount()) + " byte(s)");
            mIn.skipBytes(position - mCountIn.getCount());
        }
//          typeFlags必须是0
        if (typeFlags == 1) {
//            检测到稀有资源
            LOGGER.info("Sparse type flags detected: " + mTypeSpec.getName());
        }
//        读取entryOffsets 数组
        int[] entryOffsets = mIn.readIntArray(entryCount);

//      ResTable_config 是否无效资源 size > 56 且超过部分不为0
        if (flags.isInvalid) {
            String resName = mTypeSpec.getName() + flags.getQualifiers();
//            打印日志
            if (mKeepBroken) {
                LOGGER.warning("Invalid config flags detected: " + resName);
            } else {
                LOGGER.warning("Invalid config flags detected. Dropping resources: " + resName);
            }
        }

//        检测到无效资源，并不保持资源完整（默认情况）赋值为null
        mType = flags.isInvalid && !mKeepBroken ? null : mPkg.getOrCreateConfig(flags);
//       偏移值跟EntryData的映射
        HashMap<Integer, EntryData> offsetsToEntryData = new HashMap<Integer, EntryData>();

//        遍历赋值
        for (int offset : entryOffsets) {
            if (offset == -1 || offsetsToEntryData.containsKey(offset)) {
                continue;
            }
//           偏移值跟EntryData的映射
            offsetsToEntryData.put(offset, readEntryData());
        }

//        遍历设置 mMissingResSpecs
        for (int i = 0; i < entryOffsets.length; i++) {
            if (entryOffsets[i] != -1) {
                mMissingResSpecs[i] = false;
//                计算ResId
                mResId = (mResId & 0xffff0000) | i;
//                通过偏移拿到 EntryData
                EntryData entryData = offsetsToEntryData.get(entryOffsets[i]);
                readEntry(entryData);
            }
        }

        return mType;
    }


    /**
     * 读取EntryData 块
     *
     * @return EntryData
     * @throws IOException
     * @throws AndrolibException
     */
    private EntryData readEntryData() throws IOException, AndrolibException {
//        ResTable_map_entry 下的 ResTable_entry 的size
//        此结构中的字节数
        short size = mIn.readShort();
        if (size < 0) {
            throw new AndrolibException("Entry size is under 0 bytes.");
        }

//      flags
        short flags = mIn.readShort();

//        ResStringPool_ref 的index 索引到字符串池表中
        int specNamesId = mIn.readInt();
//      如果true 读取ResTable_map下的Res_value 块 否则
        ResValue value = (flags & ENTRY_FLAG_COMPLEX) == 0 ? readValue() : readComplexEntry();
        EntryData entryData = new EntryData();
        entryData.mFlags = flags;
        entryData.mSpecNamesId = specNamesId;
        entryData.mValue = value;
        return entryData;
    }

    /**
     * 读EntryData
     *
     * @param entryData
     * @throws AndrolibException
     */
    private void readEntry(EntryData entryData) throws AndrolibException {
        int specNamesId = entryData.mSpecNamesId;
        ResValue value = entryData.mValue;

        if (mTypeSpec.isString() && value instanceof ResFileValue) {
            value = new ResStringValue(value.toString(), ((ResFileValue) value).getRawIntValue());
        }
        if (mType == null) {
            return;
        }

        ResID resId = new ResID(mResId);
        ResResSpec spec;
        if (mPkg.hasResSpec(resId)) {
            spec = mPkg.getResSpec(resId);

            if (spec.isDummyResSpec()) {
                removeResSpec(spec);

                spec = new ResResSpec(resId, mSpecNames.getString(specNamesId), mPkg, mTypeSpec);
                mPkg.addResSpec(spec);
                mTypeSpec.addResSpec(spec);
            }
        } else {
            spec = new ResResSpec(resId, mSpecNames.getString(specNamesId), mPkg, mTypeSpec);
            mPkg.addResSpec(spec);
            mTypeSpec.addResSpec(spec);
        }
        ResResource res = new ResResource(mType, spec, value);

        try {
            mType.addResource(res);
            spec.addResource(res);
        } catch (AndrolibException ex) {
            if (mKeepBroken) {
                mType.addResource(res, true);
                spec.addResource(res, true);
                LOGGER.warning(String.format("Duplicate Resource Detected. Ignoring duplicate: %s", res.toString()));
            } else {
                throw ex;
            }
        }
    }

    /**
     * 解析ResTable_map_entry下的ResTable_ref
     *
     * @return ResBagValue
     * @throws IOException       IO异常
     * @throws AndrolibException 自定义异常
     */
    private ResBagValue readComplexEntry() throws IOException, AndrolibException {
//        indent
        int parent = mIn.readInt();
//        count
        int count = mIn.readInt();

        ResValueFactory factory = mPkg.getValueFactory();
        Duo<Integer, ResScalarValue>[] items = new Duo[count];
        ResIntBasedValue resValue;
        int resId;

        for (int i = 0; i < count; i++) {
//            indent
            resId = mIn.readInt();
//             读取ResTable_map下的Res_value 块
            resValue = readValue();

            if (resValue instanceof ResScalarValue) {
                items[i] = new Duo<Integer, ResScalarValue>(resId, (ResScalarValue) resValue);
            } else {
                resValue = new ResStringValue(resValue.toString(), resValue.getRawIntValue());
                items[i] = new Duo<Integer, ResScalarValue>(resId, (ResScalarValue) resValue);
            }
        }

        return factory.bagFactory(parent, items, mTypeSpec);
    }

    /**
     * 读取ResTable_map下的Res_value 块
     *
     * @return ResIntBasedValue
     * @throws IOException       IO异常
     * @throws AndrolibException 自定义异常
     */
    private ResIntBasedValue readValue() throws IOException, AndrolibException {
        /* size */
        mIn.skipCheckShort((short) 8);
        /* zero */
//        res0 一直都是0
        mIn.skipCheckByte((byte) 0);

//        数据值的类型
        byte type = mIn.readByte();
//        数据值
        int data = mIn.readInt();

//        是否是字符串类型
        return type == TypedValue.TYPE_STRING
            ? mPkg.getValueFactory().factory(mTableStrings.getHTML(data), data)
            : mPkg.getValueFactory().factory(type, data, null);
    }

    /**
     * 读取ResTable_config
     *
     * @return ResConfigFlags
     * @throws IOException       IO异常
     * @throws AndrolibException 自定义异常
     */
    private ResConfigFlags readConfigFlags() throws IOException, AndrolibException {
//        此结构中的字节数
        int size = mIn.readInt();
        int read = 28;

        if (size < 28) {
            throw new AndrolibException("Config size < 28");
        }

//        是否无效资源
        boolean isInvalid = false;

//      移动设备网络代码（MNC）是与移动设备国家代码（MCC）
        short mcc = mIn.readShort();
        short mnc = mIn.readShort();

//      获取语言
        char[] language = this.unpackLanguageOrRegion(mIn.readByte(), mIn.readByte(), 'a');
//      获取地区
        char[] country = this.unpackLanguageOrRegion(mIn.readByte(), mIn.readByte(), '0');

//        方向
        byte orientation = mIn.readByte();
//        触摸
        byte touchscreen = mIn.readByte();

//        密度
        int density = mIn.readUnsignedShort();

//       键盘
        byte keyboard = mIn.readByte();
//        导航
        byte navigation = mIn.readByte();
//        输入标记
        byte inputFlags = mIn.readByte();
        /* inputPad0 */
        mIn.skipBytes(1);

//       屏幕宽度
        short screenWidth = mIn.readShort();
//        屏幕高度
        short screenHeight = mIn.readShort();

//        sdk版本
        short sdkVersion = mIn.readShort();
        /* minorVersion, now must always be 0 */
        mIn.skipBytes(2);

//        屏幕布局
        byte screenLayout = 0;
        byte uiMode = 0;
//        最小的屏幕宽度Dp
        short smallestScreenWidthDp = 0;
        if (size >= 32) {
            screenLayout = mIn.readByte();
            uiMode = mIn.readByte();
            smallestScreenWidthDp = mIn.readShort();
            read = 32;
        }

        short screenWidthDp = 0;
        short screenHeightDp = 0;
        if (size >= 36) {
            screenWidthDp = mIn.readShort();
            screenHeightDp = mIn.readShort();
            read = 36;
        }

        char[] localeScript = null;
        char[] localeVariant = null;
        if (size >= 48) {
            localeScript = readScriptOrVariantChar(4).toCharArray();
            localeVariant = readScriptOrVariantChar(8).toCharArray();
            read = 48;
        }

        byte screenLayout2 = 0;
        byte colorMode = 0;
        if (size >= 52) {
            screenLayout2 = mIn.readByte();
            colorMode = mIn.readByte();
            mIn.skipBytes(2); // reserved padding
            read = 52;
        }

        if (size >= 56) {
            mIn.skipBytes(4);
            read = 56;
        }

        int exceedingSize = size - KNOWN_CONFIG_BYTES;
        if (exceedingSize > 0) {
            byte[] buf = new byte[exceedingSize];
            read += exceedingSize;
            mIn.readFully(buf);
            BigInteger exceedingBI = new BigInteger(1, buf);

            if (exceedingBI.equals(BigInteger.ZERO)) {
//                打印日志，  因为size > 56  ,且多出部分都是0
                LOGGER.fine(String
                    .format("Config flags size > %d, but exceeding bytes are all zero, so it should be ok.",
                        KNOWN_CONFIG_BYTES));
            } else {
//                打印警告， 因为size > 56  ,且多出部分不为0
                LOGGER.warning(String.format("Config flags size > %d. Size = %d. Exceeding bytes: 0x%X.",
                    KNOWN_CONFIG_BYTES, size, exceedingBI));
                isInvalid = true;
            }
        }

        int remainingSize = size - read;
//        再次确保读完 ResTable_config 块
        if (remainingSize > 0) {
            mIn.skipBytes(remainingSize);
        }

        return new ResConfigFlags(mcc, mnc, language, country,
            orientation, touchscreen, density, keyboard, navigation,
            inputFlags, screenWidth, screenHeight, sdkVersion,
            screenLayout, uiMode, smallestScreenWidthDp, screenWidthDp,
            screenHeightDp, localeScript, localeVariant, screenLayout2,
            colorMode, isInvalid, size);
    }

    /**
     * 拆包语言or地区
     *
     * @param in0
     * @param in1
     * @param base
     * @return
     */
    private char[] unpackLanguageOrRegion(byte in0, byte in1, char base) {
        // check high bit, if so we have a packed 3 letter code
        if (((in0 >> 7) & 1) == 1) {
            int first = in1 & 0x1F;
            int second = ((in1 & 0xE0) >> 5) + ((in0 & 0x03) << 3);
            int third = (in0 & 0x7C) >> 2;

            // since this function handles languages & regions, we add the value(s) to the base char
            // which is usually 'a' or '0' depending on language or region.
            return new char[]{(char) (first + base), (char) (second + base), (char) (third + base)};
        }
        return new char[]{(char) in0, (char) in1};
    }

    private String readScriptOrVariantChar(int length) throws IOException {
        StringBuilder string = new StringBuilder(16);

        while (length-- != 0) {
            short ch = mIn.readByte();
            if (ch == 0) {
                break;
            }
            string.append((char) ch);
        }
        mIn.skipBytes(length);

        return string.toString();
    }

    /**
     * 添加属性类型
     *
     * @param resTypeSpec ResTypeSpec
     */
    private void addTypeSpec(ResTypeSpec resTypeSpec) {
        mResTypeSpecs.put(resTypeSpec.getId(), resTypeSpec);
    }

    /**
     * 添加未读值， 未知无法读取
     * 为@null引用
     *
     * @throws AndrolibException 自定义异常
     */
    private void addMissingResSpecs() throws AndrolibException {
//        后(2进制)16位，（16进制）4位置为0
        int resId = mResId & 0xffff0000;

        for (int i = 0; i < mMissingResSpecs.length; i++) {
            if (!mMissingResSpecs[i]) {
                continue;
            }

//            构建ResResSpec值
            ResResSpec spec = new ResResSpec(new ResID(resId | i), "APKTOOL_DUMMY_" + Integer.toHexString(i), mPkg, mTypeSpec);

            // If we already have this resID dont add it again.
//            如果我们已经有这个残基了，就不要再加了。
            if (!mPkg.hasResSpec(new ResID(resId | i))) {
                mPkg.addResSpec(spec);
                mTypeSpec.addResSpec(spec);

                if (mType == null) {
                    mType = mPkg.getOrCreateConfig(new ResConfigFlags());
                }

                // We are going to make dummy attributes a null reference (@null) now instead of a boolean false.
                // This is because aapt2 is much more strict when it comes to what we can put in an application.
//                现在，我们将使虚拟属性成为一个空引用(@null)，而不是布尔值false。
//                这是因为aapt2对于我们可以放入应用程序的内容要严格得多。
                ResValue value = new ResReferenceValue(mPkg, 0, "");

                ResResource res = new ResResource(mType, spec, value);
                mType.addResource(res);
                spec.addResource(res);
            }
        }
    }

    private void removeResSpec(ResResSpec spec) throws AndrolibException {
        if (mPkg.hasResSpec(spec.getId())) {
            mPkg.removeResSpec(spec);
            mTypeSpec.removeResSpec(spec);
        }
    }

    /**
     * 获取下一块的头
     *
     * @return Header 整个头
     * @throws IOException IO异常
     */
    private Header nextChunk() throws IOException {
        return mHeader = Header.read(mIn, mCountIn);
    }

    /**
     * 检查当前块头的类型
     *
     * @param expectedType
     * @throws AndrolibException 自定义异常
     */
    private void checkChunkType(int expectedType) throws AndrolibException {
        if (mHeader.type != expectedType) {
            throw new AndrolibException(String.format("Invalid chunk type: expected=0x%08x, got=0x%08x",
                expectedType, mHeader.type));
        }
    }

    /**
     * 读取下chunk的头部信息
     * 坚持头部信息
     * 指针移动到头的尾部
     *
     * @param expectedType 预期的类型
     * @throws IOException       IO异常
     * @throws AndrolibException 自定义异常
     */
    private void nextChunkCheckType(int expectedType) throws IOException, AndrolibException {
//      跳转到下一块
        nextChunk();
        checkChunkType(expectedType);
    }

    /**
     * 待解码的数据流
     */
    private final ExtDataInput mIn;
    private final ResTable mResTable;
    /**
     * 它计算到目前为止已经通过流的字节数
     */
    private final CountingInputStream mCountIn;
    private final List<FlagsOffset> mFlagsOffsets;
    private final boolean mKeepBroken;

    private Header mHeader;
    private StringBlock mTableStrings;
    /**
     * TablePackageType下的StringPoolType下的ResStringPooL_string 数组
     */
    private StringBlock mTypeNames;
    /**
     * package下的ResStringPool_keyStrings
     */
    private StringBlock mSpecNames;
    /**
     * Res包名， 私有为0x7f
     */
    private ResPackage mPkg;
    private ResTypeSpec mTypeSpec;
    private ResType mType;
    private int mResId;
    private int mTypeIdOffset = 0;
    private boolean[] mMissingResSpecs;
    private HashMap<Integer, ResTypeSpec> mResTypeSpecs = new HashMap<>();

    private final static short ENTRY_FLAG_COMPLEX = 0x0001;
    private final static short ENTRY_FLAG_PUBLIC = 0x0002;
    private final static short ENTRY_FLAG_WEAK = 0x0004;

    public static class Header {
        public final short type;
        public final int headerSize;
        public final int chunkSize;
        public final int startPosition;
        public final int endPosition;

        public Header(short type, int headerSize, int chunkSize, int headerStart) {
            this.type = type;
            this.headerSize = headerSize;
            this.chunkSize = chunkSize;
            this.startPosition = headerStart;
            this.endPosition = headerStart + chunkSize;
        }

        public static Header read(ExtDataInput in, CountingInputStream countIn) throws IOException {
            short type;
            int start = countIn.getCount();
            try {
                type = in.readShort();
            } catch (EOFException ex) {
                return new Header(TYPE_NONE, 0, 0, countIn.getCount());
            }
            return new Header(type, in.readShort(), in.readInt(), start);
        }

        public final static short TYPE_NONE = -1, TYPE_TABLE = 0x0002,
            TYPE_PACKAGE = 0x0200, TYPE_TYPE = 0x0201, TYPE_SPEC_TYPE = 0x0202, TYPE_LIBRARY = 0x0203;
    }

    public static class FlagsOffset {
        public final int offset;
        public final int count;

        public FlagsOffset(int offset, int count) {
            this.offset = offset;
            this.count = count;
        }
    }

    private class EntryData {
        public short mFlags;
        public int mSpecNamesId;
        public ResValue mValue;
    }

    private static final Logger LOGGER = Logger.getLogger(ARSCDecoder.class.getName());
    private static final int KNOWN_CONFIG_BYTES = 56;

    /**
     * 解码后的ARSCData
     */
    public static class ARSCData {

        /**
         * 解码后的ARSCData
         *
         * @param packages     解码后的Package资源
         * @param flagsOffsets 位移
         * @param resTable     ResTable
         */
        public ARSCData(ResPackage[] packages, FlagsOffset[] flagsOffsets, ResTable resTable) {
            mPackages = packages;
            mFlagsOffsets = flagsOffsets;
            mResTable = resTable;
        }

        public FlagsOffset[] getFlagsOffsets() {
            return mFlagsOffsets;
        }

        public ResPackage[] getPackages() {
            return mPackages;
        }

        /**
         * 获得一个Package
         *
         * @return ResPackage
         * @throws AndrolibException 自定义异常
         */
        public ResPackage getOnePackage() throws AndrolibException {
            if (mPackages.length <= 0) {
                throw new AndrolibException("Arsc file contains zero packages");
            } else if (mPackages.length != 1) {
                int id = findPackageWithMostResSpecs();
                LOGGER.info("Arsc file contains multiple packages. Using package "
                    + mPackages[id].getName() + " as default.");

                return mPackages[id];
            }
            return mPackages[0];
        }

        /**
         * 找到最多ResSpecs的Package
         *
         * @return int 下标
         */
        public int findPackageWithMostResSpecs() {
            int count = mPackages[0].getResSpecCount();
            int id = 0;

            for (int i = 0; i < mPackages.length; i++) {
                if (mPackages[i].getResSpecCount() >= count) {
                    count = mPackages[i].getResSpecCount();
                    id = i;
                }
            }
            return id;
        }

        public ResTable getResTable() {
            return mResTable;
        }

        private final ResPackage[] mPackages;
        private final FlagsOffset[] mFlagsOffsets;
        private final ResTable mResTable;
    }
}
