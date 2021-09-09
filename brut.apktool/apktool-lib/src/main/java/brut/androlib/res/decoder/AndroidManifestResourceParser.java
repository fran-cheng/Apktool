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

import java.util.regex.Pattern;

/**
 * AXmlResourceParser specifically for parsing encoded AndroidManifest.xml.
 * 专门用于解析编码的AndroidManifest.xml的AXmlResourceParser。
 */
public class AndroidManifestResourceParser extends AXmlResourceParser {

    /**
     * Pattern for matching numeric string meta-data values. aapt automatically infers the
     * type for a manifest meta-data value based on the string in the unencoded XML. However,
     * some apps intentionally coerce integers to be strings by prepending an escaped space.
     * For details/discussion, see https://stackoverflow.com/questions/2154945/how-to-force-a-meta-data-value-to-type-string
     * With aapt1, the escaped space is dropped when encoded. For aapt2, the escaped space is preserved.
     * <p>
     * *匹配数字字符串元数据值的模式。Aapt自动推断
     * 类型用于基于未编码XML中的字符串的清单元数据值。然而,
     * *一些应用程序故意通过前置转义空格来强制整数为字符串。
     * *详情/讨论请参见https://stackoverflow.com/questions/2154945/how-to-force-a-meta-data-value-to-type-string
     * *使用aapt1，转义的空间在编码时被删除。对于aapt2，保留转义的空间。
     */
    private static final Pattern PATTERN_NUMERIC_STRING = Pattern.compile("\\s?\\d+");

    /**
     * 获得属性值
     *
     * @param index
     * @return String
     */
    @Override
    public String getAttributeValue(int index) {
        String value = super.getAttributeValue(index);

        if (!isNumericStringMetadataAttributeValue(index, value)) {
            return value;
        }

        // Patch the numeric string value by prefixing it with an escaped space.
        // Otherwise, when the decoded app is rebuilt, aapt will incorrectly encode
        // the value as an int or float (depending on aapt version), breaking the original
        // app functionality.
//        通过前缀转义空格来修补数字字符串值。
//        否则，当解码后的应用程序重新构建时，aapt将错误地编码
//        该值作为一个int或float(取决于aapt版本)，破坏了原来的值
//        应用程序的功能。
        return "\\ " + super.getAttributeValue(index).trim();
    }

    /**
     * 判断该元数据是否是int
     *
     * @param index 下标
     * @param value 值
     * @return boolean
     */
    private boolean isNumericStringMetadataAttributeValue(int index, String value) {
        return "meta-data".equalsIgnoreCase(super.getName())
            && "value".equalsIgnoreCase(super.getAttributeName(index))
            && super.getAttributeValueType(index) == TypedValue.TYPE_STRING
            && PATTERN_NUMERIC_STRING.matcher(value).matches();
    }
}
