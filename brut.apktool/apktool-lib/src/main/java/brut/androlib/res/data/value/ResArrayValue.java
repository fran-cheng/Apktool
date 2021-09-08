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
import brut.androlib.res.xml.ResValuesXmlSerializable;
import brut.util.Duo;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.Arrays;

/**
 * arrays.xlm
 */
public class ResArrayValue extends ResBagValue implements
    ResValuesXmlSerializable {

    /**
     * ResArrayValue
     *
     * @param parent Res参考值
     * @param items
     */
    ResArrayValue(ResReferenceValue parent, Duo<Integer, ResScalarValue>[] items) {
        super(parent);

        mItems = new ResScalarValue[items.length];
        for (int i = 0; i < items.length; i++) {
            mItems[i] = items[i].m2;
        }
    }

    public ResArrayValue(ResReferenceValue parent, ResScalarValue[] items) {
        super(parent);
        mItems = items;
    }

    /**
     * 序列化成arrays.xml
     *
     * @param serializer XmlSerializer
     * @param res        ResResource
     * @throws IOException       IO异常
     * @throws AndrolibException 自定义异常
     */
    @Override
    public void serializeToResValuesXml(XmlSerializer serializer,
                                        ResResource res) throws IOException, AndrolibException {
//        获得类型
        String type = getType();
//        type为null  则 为 array 否则例如  string-array
        type = (type == null ? "" : type + "-") + "array";
//        序列化开始标签为<array>、
        serializer.startTag(null, type);
//        设置标签属性 name
        serializer.attribute(null, "name", res.getResSpec().getName());

        // lets check if we need to add formatted="false" to this array
//        让我们检查是否需要添加formatted="false"到这个array
        for (int i = 0; i < mItems.length; i++) {
            if (mItems[i].hasMultipleNonPositionalSubstitutions()) {
                serializer.attribute(null, "formatted", "false");
                break;
            }
        }

//        添加<item>标签
        // add <item>'s
        for (int i = 0; i < mItems.length; i++) {
            serializer.startTag(null, "item");
            serializer.text(mItems[i].encodeAsResXmlNonEscapedItemValue());
            serializer.endTag(null, "item");
        }
        serializer.endTag(null, type);
    }

    /**
     * 获得类型
     *
     * @return String
     * @throws AndrolibException 自定义异常
     */
    public String getType() throws AndrolibException {
        if (mItems.length == 0) {
            return null;
        }
        String type = mItems[0].getType();
        for (int i = 0; i < mItems.length; i++) {
            if (mItems[i].encodeAsResXmlItemValue().startsWith("@string")) {
//                类型为String
                return "string";
            } else if (mItems[i].encodeAsResXmlItemValue().startsWith("@drawable")) {
                return null;
            } else if (mItems[i].encodeAsResXmlItemValue().startsWith("@integer")) {
//                类型为integer
                return "integer";
            } else if (!"string".equals(type) && !"integer".equals(type)) {
                return null;
            } else if (!type.equals(mItems[i].getType())) {
                return null;
            }
        }
        if (!Arrays.asList(AllowedArrayTypes).contains(type)) {
            return "string";
        }
        return type;
    }

    private final ResScalarValue[] mItems;
    /**
     * 允许的Array类型
     */
    private final String[] AllowedArrayTypes = {"string", "integer"};

    /**
     * 标记
     */
    public static final int BAG_KEY_ARRAY_START = 0x02000000;
}
