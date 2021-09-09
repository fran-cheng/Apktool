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
import brut.androlib.res.xml.ResXmlEncodable;
import brut.androlib.res.xml.ResXmlEncoders;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * Res标量值（标准？）
 * 将原始的 32位mRawValue  转换成了
 */
public abstract class ResScalarValue extends ResIntBasedValue implements
    ResXmlEncodable, ResValuesXmlSerializable {
    /**
     * 属性类型，color，dimension，reference
     */
    protected final String mType;
    /**
     * 原始的值，如"color","dimen","fraction"等，可能为null
     */
    protected final String mRawValue;

    protected ResScalarValue(String type, int rawIntValue, String rawValue) {
        super(rawIntValue);
        mType = type;
        mRawValue = rawValue;
    }

    /**
     * 实现ResXmlEncodable 的 encodeAsResXmlAttr
     *
     * @return 属性的类型值
     * @throws AndrolibException 自定义异常
     */
    @Override
    public String encodeAsResXmlAttr() throws AndrolibException {
        if (mRawValue != null) {
            return mRawValue;
        }
        return encodeAsResXml();
    }

    /**
     * 解码获得xml 的Item标签的值
     *
     * @return String
     * @throws AndrolibException 自定义异常
     */
    public String encodeAsResXmlItemValue() throws AndrolibException {
        return encodeAsResXmlValue();
    }

    /**
     * 实现ResXmlEncodable 的 encodeAsResXmlValue
     *
     * @return 属性的原始值
     * @throws AndrolibException 自定义异常
     */
    @Override
    public String encodeAsResXmlValue() throws AndrolibException {
        if (mRawValue != null) {
            return mRawValue;
        }
        return encodeAsResXml();
    }

    /**
     * 解码xml文件的Item值
     *
     * @return 解码后获得的String
     * @throws AndrolibException 自定义异常
     */
    public String encodeAsResXmlNonEscapedItemValue() throws AndrolibException {
        return encodeAsResXmlValue().replace("&amp;", "&").replace("&lt;", "<");
    }

    public boolean hasMultipleNonPositionalSubstitutions() throws AndrolibException {
        return ResXmlEncoders.hasMultipleNonPositionalSubstitutions(mRawValue);
    }

    /**
     * 序列化成xml
     *
     * @param serializer XmlSerializer
     * @param res        ResResource
     * @throws IOException       IO异常
     * @throws AndrolibException 自定义异常
     */
    @Override
    public void serializeToResValuesXml(XmlSerializer serializer,
                                        ResResource res) throws IOException, AndrolibException {
//        资源文件名
        String type = res.getResSpec().getType().getName();
//        标签是否位item
        boolean item = !"reference".equals(mType) && !type.equals(mType);

        String body = encodeAsResXmlValue();

        // check for resource reference
//        查看资源参考
        if (!type.equalsIgnoreCase("color")) {

            if (body.contains("@")) {
                if (!res.getFilePath().contains("string")) {
                    item = true;
                }
            }
        }

        // Dummy attributes should be <item> with type attribute
//        虚拟属性应该是<item> with type attribute
        if (res.getResSpec().isDummyResSpec()) {
            item = true;
        }

        // Android does not allow values (false) for ids.xml anymore
        // https://issuetracker.google.com/issues/80475496
        // But it decodes as a ResBoolean, which makes no sense. So force it to empty
//        Android不再允许ids.xml的值为false
//        https://issuetracker.google.com/issues/80475496
//        但它解码为ResBoolean，这没有意义。所以把它赋空
//        AAPT2 只允许以下形式的
//          id ：<item type=id" name="my_id/>
//               <item type=id" name="my_id></item>
//<              <item type=id" name="my_id>@id/ other_id</item> <!-- @id/my_id 是对@id/other_id 的引用-->
//
        if (type.equalsIgnoreCase("id") && !body.isEmpty()) {
            body = "";
        }

        // check for using attrib as node or item
//        检查是否使用attrib作为节点或项
        String tagName = item ? "item" : type;

        serializer.startTag(null, tagName);
        if (item) {
            serializer.attribute(null, "type", type);
        }
        serializer.attribute(null, "name", res.getResSpec().getName());

        serializeExtraXmlAttrs(serializer, res);

        if (!body.isEmpty()) {
//            不为空置空
            serializer.ignorableWhitespace(body);
        }

        serializer.endTag(null, tagName);
    }

    /**
     * 获取类型
     *
     * @return String
     */
    public String getType() {
        return mType;
    }

    /**
     * 连续的xml标签 属性
     *
     * @param serializer XmlSerializer
     * @param res        ResResource
     * @throws IOException IO异常
     */
    protected void serializeExtraXmlAttrs(XmlSerializer serializer,
                                          ResResource res) throws IOException {
    }

    protected abstract String encodeAsResXml() throws AndrolibException;
}
