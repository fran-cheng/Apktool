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
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResResource;
import brut.androlib.res.xml.ResValuesXmlSerializable;
import brut.util.Duo;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * Res的属性
 */
public class ResAttr extends ResBagValue implements ResValuesXmlSerializable {
    ResAttr(ResReferenceValue parentVal, int type, Integer min, Integer max,
            Boolean l10n) {
        super(parentVal);
        mType = type;
        mMin = min;
        mMax = max;
        mL10n = l10n;
    }

    public String convertToResXmlFormat(ResScalarValue value)
        throws AndrolibException {
        return null;
    }

    /**
     * 序列化成Value下的xml
     *
     * @param serializer XmlSerializer
     * @param res        Res资源
     * @throws IOException       IO异常
     * @throws AndrolibException 自定义异常
     */
    @Override
    public void serializeToResValuesXml(XmlSerializer serializer,
                                        ResResource res) throws IOException, AndrolibException {
        String type = getTypeAsString();

        serializer.startTag(null, "attr");
        serializer.attribute(null, "name", res.getResSpec().getName());
        if (type != null) {
            serializer.attribute(null, "format", type);
        }
        if (mMin != null) {
            serializer.attribute(null, "min", mMin.toString());
        }
        if (mMax != null) {
            serializer.attribute(null, "max", mMax.toString());
        }
        if (mL10n != null && mL10n) {
            serializer.attribute(null, "localization", "suggested");
        }
        serializeBody(serializer, res);
        serializer.endTag(null, "attr");
    }

    /**
     * 静态方法， 创建ResAttr
     *
     * @param parent  ResReferenceValue
     * @param items   Duo<Integer, ResScalarValue>[]
     * @param factory ResValueFactory
     * @param pkg     ResPackage
     * @return ResAttr
     * @throws AndrolibException 自定义异常
     */
    public static ResAttr factory(ResReferenceValue parent,
                                  Duo<Integer, ResScalarValue>[] items, ResValueFactory factory,
                                  ResPackage pkg) throws AndrolibException {

//        获取类型
        int type = ((ResIntValue) items[0].m2).getValue();
//       &16位
        int scalarType = type & 0xffff;
        Integer min = null, max = null;
        Boolean l10n = null;
        int i;
        for (i = 1; i < items.length; i++) {
//            选择ResTable_map_enrty下的ResTable_map下的ResTable_ref 即indent
            switch (items[i].m1) {
                case BAG_KEY_ATTR_MIN:
                    min = ((ResIntValue) items[i].m2).getValue();
                    continue;
                case BAG_KEY_ATTR_MAX:
                    max = ((ResIntValue) items[i].m2).getValue();
                    continue;
                case BAG_KEY_ATTR_L10N:
                    l10n = ((ResIntValue) items[i].m2).getValue() != 0;
                    continue;
            }
            break;
        }

//        如果for循环遍历完成,则用先用数据构建ResAttr
        if (i == items.length) {
            return new ResAttr(parent, scalarType, min, max, l10n);
        }
//        没有遍历到的存放到attrItems
        Duo<ResReferenceValue, ResIntValue>[] attrItems = new Duo[items.length
            - i];
        int j = 0;
        for (; i < items.length; i++) {
            int resId = items[i].m1;
//            没有遍历到的resId,添加到ResPackage
            pkg.addSynthesizedRes(resId);
            attrItems[j++] = new Duo<ResReferenceValue, ResIntValue>(
                factory.newReference(resId, null),
                (ResIntValue) items[i].m2);
        }
        switch (type & 0xff0000) {
            case TYPE_ENUM:
                return new ResEnumAttr(parent, scalarType, min, max, l10n,
                    attrItems);
            case TYPE_FLAGS:
                return new ResFlagsAttr(parent, scalarType, min, max, l10n,
                    attrItems);
        }

        throw new AndrolibException("Could not decode attr value");
    }

    protected void serializeBody(XmlSerializer serializer, ResResource res)
        throws AndrolibException, IOException {
    }

    /**
     * 将mType转换成String
     *
     * @return String
     */
    protected String getTypeAsString() {
        String s = "";
        if ((mType & TYPE_REFERENCE) != 0) {
//            reference
            s += "|reference";
        }
        if ((mType & TYPE_STRING) != 0) {
//            string
            s += "|string";
        }
        if ((mType & TYPE_INT) != 0) {
//            integer
            s += "|integer";
        }
        if ((mType & TYPE_BOOL) != 0) {
//            boolean
            s += "|boolean";
        }
        if ((mType & TYPE_COLOR) != 0) {
//            color
            s += "|color";
        }
        if ((mType & TYPE_FLOAT) != 0) {
//            float
            s += "|float";
        }
        if ((mType & TYPE_DIMEN) != 0) {
//            dimension
            s += "|dimension";
        }
        if ((mType & TYPE_FRACTION) != 0) {
//            fraction
            s += "|fraction";
        }
        if (s.isEmpty()) {
            return null;
        }
        return s.substring(1);
    }

    /**
     * 类型的值
     */
    private final int mType;
    private final Integer mMin;
    private final Integer mMax;
    private final Boolean mL10n;

    public static final int BAG_KEY_ATTR_TYPE = 0x01000000;
    private static final int BAG_KEY_ATTR_MIN = 0x01000001;
    private static final int BAG_KEY_ATTR_MAX = 0x01000002;
    private static final int BAG_KEY_ATTR_L10N = 0x01000003;

    private final static int TYPE_REFERENCE = 0x01;
    private final static int TYPE_STRING = 0x02;
    private final static int TYPE_INT = 0x04;
    private final static int TYPE_BOOL = 0x08;
    private final static int TYPE_COLOR = 0x10;
    private final static int TYPE_FLOAT = 0x20;
    private final static int TYPE_DIMEN = 0x40;
    private final static int TYPE_FRACTION = 0x80;
    private final static int TYPE_ANY_STRING = 0xee;

    private static final int TYPE_ENUM = 0x00010000;
    private static final int TYPE_FLAGS = 0x00020000;
}
