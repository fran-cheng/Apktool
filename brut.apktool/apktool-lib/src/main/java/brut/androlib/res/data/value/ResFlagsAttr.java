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
package brut.androlib.res.data.value;

import brut.androlib.AndrolibException;
import brut.androlib.res.data.ResResource;
import brut.util.Duo;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Res标记标签属性的转换
 */
public class ResFlagsAttr extends ResAttr {
    ResFlagsAttr(ResReferenceValue parent, int type, Integer min, Integer max,
                 Boolean l10n, Duo<ResReferenceValue, ResIntValue>[] items) {
        super(parent, type, min, max, l10n);

        mItems = new FlagItem[items.length];
        for (int i = 0; i < items.length; i++) {
            mItems[i] = new FlagItem(items[i].m1, items[i].m2.getValue());
        }
    }

    /**
     * 格式化成XML
     *
     * @param value ResScalarValue
     * @return String
     * @throws AndrolibException 自定义异常
     */
    @Override
    public String convertToResXmlFormat(ResScalarValue value)
        throws AndrolibException {
        if (value instanceof ResReferenceValue) {
            return value.encodeAsResXml();
        }
        if (!(value instanceof ResIntValue)) {
            return super.convertToResXmlFormat(value);
        }
//        初始化mFlag 和 mZeroFlags
        loadFlags();
        int intVal = ((ResIntValue) value).getValue();

        if (intVal == 0) {
            return renderFlags(mZeroFlags);
        }

        FlagItem[] flagItems = new FlagItem[mFlags.length];
        int[] flags = new int[mFlags.length];
        int flagsCount = 0;
        for (int i = 0; i < mFlags.length; i++) {
            FlagItem flagItem = mFlags[i];
            int flag = flagItem.flag;

            if ((intVal & flag) != flag) {
                continue;
            }

            if (!isSubpartOf(flag, flags)) {
                flags[flagsCount] = flag;
                flagItems[flagsCount++] = flagItem;
            }
        }
        return renderFlags(Arrays.copyOf(flagItems, flagsCount));
    }

    /**
     * 序列化body
     *
     * @param serializer XmlSerializer
     * @param res        ResResource
     * @throws AndrolibException 自定义异常
     * @throws IOException       IO异常
     */
    @Override
    protected void serializeBody(XmlSerializer serializer, ResResource res)
        throws AndrolibException, IOException {
        for (int i = 0; i < mItems.length; i++) {
            FlagItem item = mItems[i];

            serializer.startTag(null, "flag");
            serializer.attribute(null, "name", item.getValue());
            serializer.attribute(null, "value",
                String.format("0x%08x", item.flag));
            serializer.endTag(null, "flag");
        }
    }

    private boolean isSubpartOf(int flag, int[] flags) {
        for (int i = 0; i < flags.length; i++) {
            if ((flags[i] & flag) == flag) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取 flags
     *
     * @param flags FlagItem[]
     * @return String
     * @throws AndrolibException 自定义异常
     */
    private String renderFlags(FlagItem[] flags) throws AndrolibException {
        String ret = "";
        for (int i = 0; i < flags.length; i++) {
            ret += "|" + flags[i].getValue();
        }
        if (ret.isEmpty()) {
            return ret;
        }
        return ret.substring(1);
    }

    /**
     * 加载标记，初始化mFlags
     */
    private void loadFlags() {
        if (mFlags != null) {
            return;
        }

        /**
         * 标记为0的Item
         */
        FlagItem[] zeroFlags = new FlagItem[mItems.length];
        int zeroFlagsCount = 0;
        FlagItem[] flags = new FlagItem[mItems.length];
        int flagsCount = 0;

        for (int i = 0; i < mItems.length; i++) {
            FlagItem item = mItems[i];
            if (item.flag == 0) {
                zeroFlags[zeroFlagsCount++] = item;
            } else {
                flags[flagsCount++] = item;
            }
        }

        mZeroFlags = Arrays.copyOf(zeroFlags, zeroFlagsCount);
        mFlags = Arrays.copyOf(flags, flagsCount);

        Arrays.sort(mFlags, new Comparator<FlagItem>() {
            @Override
            public int compare(FlagItem o1, FlagItem o2) {
                return Integer.valueOf(Integer.bitCount(o2.flag)).compareTo(
                    Integer.bitCount(o1.flag));
            }
        });
    }

    private final FlagItem[] mItems;

    /**
     * 标记为0的Item
     */
    private FlagItem[] mZeroFlags;
    /**
     * 标记非0的Item
     */
    private FlagItem[] mFlags;

    private static class FlagItem {
        public final ResReferenceValue ref;
        public final int flag;
        public String value;

        public FlagItem(ResReferenceValue ref, int flag) {
            this.ref = ref;
            this.flag = flag;
        }

        public String getValue() throws AndrolibException {
            if (value == null) {
                if (ref.referentIsNull()) {
                    return "@null";
                }
                value = ref.getReferent().getName();
            }
            return value;
        }
    }
}
