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
package brut.androlib.res.util;

import org.xmlpull.renamed.MXSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * 扩展MXSerializer，ExtXmlSerializer
 * 序列化
 */
public class ExtMXSerializer extends MXSerializer implements ExtXmlSerializer {
    /**
     * 文件开始
     *
     * @param encoding   encoding 编码格式
     * @param standalone standalone
     * @throws IOException              IO异常
     * @throws IllegalArgumentException 违例参数异常
     * @throws IllegalStateException    非法状态异常
     */
    @Override
    public void startDocument(String encoding, Boolean standalone)
        throws IOException, IllegalArgumentException, IllegalStateException {
        super.startDocument(encoding != null ? encoding : mDefaultEncoding, standalone);
        this.newLine();
    }

    /**
     * 写属性值
     *
     * @param value value
     * @param out   out
     * @throws IOException IO异常
     */
    @Override
    protected void writeAttributeValue(String value, Writer out) throws IOException {
        if (mIsDisabledAttrEscape) {
            out.write(value == null ? "" : value);
            return;
        }
        super.writeAttributeValue(value, out);
    }

    /**
     * 设置输出文件
     *
     * @param os       os
     * @param encoding 编码格式
     * @throws IOException IO异常
     */
    @Override
    public void setOutput(OutputStream os, String encoding) throws IOException {
        super.setOutput(os, encoding != null ? encoding : mDefaultEncoding);
    }

    @Override
    public Object getProperty(String name) throws IllegalArgumentException {
        if (PROPERTY_DEFAULT_ENCODING.equals(name)) {
            return mDefaultEncoding;
        }
        return super.getProperty(name);
    }

    /**
     * 设置编码格式
     *
     * @param name  name
     * @param value value
     * @throws IllegalArgumentException 违例参数
     * @throws IllegalStateException    非法状态
     */
    @Override
    public void setProperty(String name, Object value) throws IllegalArgumentException, IllegalStateException {
        if (PROPERTY_DEFAULT_ENCODING.equals(name)) {
            mDefaultEncoding = (String) value;
        } else {
            super.setProperty(name, value);
        }
    }

    /**
     * 换新行
     *
     * @return ExtXmlSerializer
     * @throws IOException IO异常
     */
    @Override
    public ExtXmlSerializer newLine() throws IOException {
        super.out.write(lineSeparator);
        return this;
    }

    /**
     * 是否避免属性逃逸
     *
     * @param disabled boolean
     */
    @Override
    public void setDisabledAttrEscape(boolean disabled) {
        mIsDisabledAttrEscape = disabled;
    }

    private String mDefaultEncoding;
    private boolean mIsDisabledAttrEscape = false;

}
