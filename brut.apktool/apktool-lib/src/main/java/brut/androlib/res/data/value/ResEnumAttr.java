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
import brut.androlib.res.data.ResResSpec;
import brut.androlib.res.data.ResResource;
import brut.util.Duo;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Res枚举标签属性的转换
 */
public class ResEnumAttr extends ResAttr {
    ResEnumAttr(ResReferenceValue parent, int type, Integer min, Integer max,
                Boolean l10n, Duo<ResReferenceValue, ResIntValue>[] items) {
        super(parent, type, min, max, l10n);
        mItems = items;
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
        if (value instanceof ResIntValue) {
            String ret = decodeValue(((ResIntValue) value).getValue());
            if (ret != null) {
                return ret;
            }
        }
        return super.convertToResXmlFormat(value);
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
        for (Duo<ResReferenceValue, ResIntValue> duo : mItems) {
            int intVal = duo.m2.getValue();
            ResResSpec m1Referent = duo.m1.getReferent();

//            标签<enum
            serializer.startTag(null, "enum");
//           属性 name
            serializer.attribute(null, "name",
                m1Referent != null ? m1Referent.getName() : "@null"
            );
            serializer.attribute(null, "value", String.valueOf(intVal));
            serializer.endTag(null, "enum");
        }
    }

    /**
     * 解码value
     *
     * @param value int
     * @return String
     * @throws AndrolibException 自定义异常
     */
    private String decodeValue(int value) throws AndrolibException {
        String value2 = mItemsCache.get(value);
        if (value2 == null) {
            ResReferenceValue ref = null;
            for (Duo<ResReferenceValue, ResIntValue> duo : mItems) {
                if (duo.m2.getValue() == value) {
                    ref = duo.m1;
                    break;
                }
            }
            if (ref != null) {
                value2 = ref.getReferent().getName();
                mItemsCache.put(value, value2);
            }
        }
        return value2;
    }

    /**
     * 存储 解码后的参考与原始数值的映射
     */
    private final Duo<ResReferenceValue, ResIntValue>[] mItems;
    /**
     * 存储 int 与解码后的String的映射
     */
    private final Map<Integer, String> mItemsCache = new HashMap<Integer, String>();
}
