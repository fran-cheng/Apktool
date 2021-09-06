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

import brut.androlib.AndrolibException;
import brut.androlib.err.AXmlDecodingException;
import brut.androlib.err.RawXmlEncounteredException;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.util.ExtXmlSerializer;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.wrapper.XmlPullParserWrapper;
import org.xmlpull.v1.wrapper.XmlPullWrapperFactory;
import org.xmlpull.v1.wrapper.XmlSerializerWrapper;
import org.xmlpull.v1.wrapper.classic.StaticXmlSerializerWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * xml流完整解码
 */
public class XmlPullStreamDecoder implements ResStreamDecoder {
    public XmlPullStreamDecoder(XmlPullParser parser,
                                ExtXmlSerializer serializer) {
        this.mParser = parser;
        this.mSerial = serializer;
    }

    /**
     * 解码的具体操作
     *
     * @param in  输入流
     * @param out 输出流
     * @throws AndrolibException 自定义异常
     */
    @Override
    public void decode(InputStream in, OutputStream out)
        throws AndrolibException {
        try {
            XmlPullWrapperFactory factory = XmlPullWrapperFactory.newInstance();
//          XmlPullParser
            XmlPullParserWrapper par = factory.newPullParserWrapper(mParser);
//            ResTable
            final ResTable resTable = ((AXmlResourceParser) mParser).getAttrDecoder().getCurrentPackage().getResTable();

            XmlSerializerWrapper ser = new StaticXmlSerializerWrapper(mSerial, factory) {
                boolean hideSdkInfo = false;
                boolean hidePackageInfo = false;

                @Override
                public void event(XmlPullParser pp)
                    throws XmlPullParserException, IOException {
                    int type = pp.getEventType();

                    if (type == XmlPullParser.START_TAG) {
//                        标签开始的位置 "<"
                        if ("manifest".equalsIgnoreCase(pp.getName())) {
                            try {
//                                解析mainfest标签
                                hidePackageInfo = parseManifest(pp);
                            } catch (AndrolibException ignored) {
                            }
                        } else if ("uses-sdk".equalsIgnoreCase(pp.getName())) {
                            try {
                                hideSdkInfo = parseAttr(pp);
                                if (hideSdkInfo) {
//                                    删除uses-sdk头标签
                                    return;
                                }
                            } catch (AndrolibException ignored) {
                            }
                        }
                    } else if (hideSdkInfo && type == XmlPullParser.END_TAG
                        && "uses-sdk".equalsIgnoreCase(pp.getName())) {
//                        删除uses-sdk尾标签
                        return;
                    } else if (hidePackageInfo && type == XmlPullParser.END_TAG
                        && "manifest".equalsIgnoreCase(pp.getName())) {
                        super.event(pp);
//                        结束
                        return;
                    }
                    super.event(pp);
                }

                /**
                 * 解析manifest标签
                 * @param pp XmlPullParser
                 * @return boolean
                 * @throws AndrolibException 自定义异常
                 */
                private boolean parseManifest(XmlPullParser pp)
                    throws AndrolibException {
                    String attr_name;

                    // read <manifest> for package:
                    for (int i = 0; i < pp.getAttributeCount(); i++) {
                        attr_name = pp.getAttributeName(i);

                        if (attr_name.equalsIgnoreCase(("package"))) {
//                            包名
                            resTable.setPackageRenamed(pp.getAttributeValue(i));
                        } else if (attr_name.equalsIgnoreCase("versionCode")) {
//                            版本code
                            resTable.setVersionCode(pp.getAttributeValue(i));
                        } else if (attr_name.equalsIgnoreCase("versionName")) {
//                            版本name
                            resTable.setVersionName(pp.getAttributeValue(i));
                        }
                    }
                    return true;
                }

                /**
                 * 解析属性
                 * @param pp XmlPullParser
                 * @return boolean
                 * @throws AndrolibException 自定义异常
                 */
                private boolean parseAttr(XmlPullParser pp)
                    throws AndrolibException {
                    for (int i = 0; i < pp.getAttributeCount(); i++) {
//                        命名空间
                        final String a_ns = "http://schemas.android.com/apk/res/android";
                        String ns = pp.getAttributeNamespace(i);

                        if (a_ns.equalsIgnoreCase(ns)) {
                            String name = pp.getAttributeName(i);
                            String value = pp.getAttributeValue(i);
                            if (name != null && value != null) {
                                if (name.equalsIgnoreCase("minSdkVersion")
                                    || name.equalsIgnoreCase("targetSdkVersion")
                                    || name.equalsIgnoreCase("maxSdkVersion")
                                    || name.equalsIgnoreCase("compileSdkVersion")) {
                                    resTable.addSdkInfo(name, value);
                                } else {
                                    resTable.clearSdkInfo();
                                    return false; // Found unknown flags
                                }
                            }
                        } else {
                            resTable.clearSdkInfo();

                            if (i >= pp.getAttributeCount()) {
                                return false; // Found unknown flags
                            }
                        }
                    }

                    return !resTable.getAnalysisMode();
                }
            };

            par.setInput(in, null);
            ser.setOutput(out, null);

            while (par.nextToken() != XmlPullParser.END_DOCUMENT) {
                ser.event(par);
            }
            ser.flush();
        } catch (XmlPullParserException ex) {
            throw new AXmlDecodingException("Could not decode XML", ex);
        } catch (IOException ex) {
            throw new RawXmlEncounteredException("Could not decode XML", ex);
        }
    }

    public void decodeManifest(InputStream in, OutputStream out)
        throws AndrolibException {
        decode(in, out);
    }

    private final XmlPullParser mParser;
    private final ExtXmlSerializer mSerial;
}
