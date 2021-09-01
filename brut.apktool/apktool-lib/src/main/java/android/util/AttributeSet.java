/*
 * Copyright 2008 Android4ME / Dmitry Skiba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.util;

/**
 * 属性组===>Set
 * XmlResourceParser
 * 从Android里面拷贝过来的
 */
public interface AttributeSet {
    int getAttributeCount();

    /**
     * 获得属性名
     *
     * @param index 下标
     * @return String
     */
    String getAttributeName(int index);


    /**
     * 获得属性值
     *
     * @param index 下标
     * @return String
     */
    String getAttributeValue(int index);

    String getPositionDescription();


    /**
     * 获得属性名资源
     *
     * @param index 下标
     * @return int
     */
    int getAttributeNameResource(int index);

    /**
     * 获取属性集合的值
     *
     * @param index        下标
     * @param options      选项
     * @param defaultValue 默认值
     * @return int
     */
    int getAttributeListValue(int index, String options[], int defaultValue);

    /**
     * 获取属性 boolean 类型
     *
     * @param index        下标
     * @param defaultValue 默认值
     * @return boolean
     */
    boolean getAttributeBooleanValue(int index, boolean defaultValue);

    /**
     * 获取属性值
     *
     * @param index        下标
     * @param defaultValue 默认值
     * @return int
     */
    int getAttributeResourceValue(int index, int defaultValue);

    /**
     * 获取属性 int 类型
     *
     * @param index        下标
     * @param defaultValue 默认值
     * @return int
     */
    int getAttributeIntValue(int index, int defaultValue);

    /**
     * 获取属性 无符号 int 类型
     *
     * @param index        下标
     * @param defaultValue 默认值
     * @return int
     */
    int getAttributeUnsignedIntValue(int index, int defaultValue);

    /**
     * 获取属性 float 类型
     *
     * @param index        下标
     * @param defaultValue 默认值
     * @return float
     */
    float getAttributeFloatValue(int index, float defaultValue);

    /**
     * 获取属性Id
     * @return String
     */
    String getIdAttribute();

    /**
     * 获取类属性
     * @return String
     */
    String getClassAttribute();

    int getIdAttributeResourceValue(int index);

    int getStyleAttribute();

    String getAttributeValue(String namespace, String attribute);

    int getAttributeListValue(String namespace, String attribute,
                              String options[], int defaultValue);

    boolean getAttributeBooleanValue(String namespace, String attribute,
                                     boolean defaultValue);

    int getAttributeResourceValue(String namespace, String attribute,
                                  int defaultValue);

    int getAttributeIntValue(String namespace, String attribute,
                             int defaultValue);

    int getAttributeUnsignedIntValue(String namespace, String attribute,
                                     int defaultValue);

    float getAttributeFloatValue(String namespace, String attribute,
                                 float defaultValue);

    // TODO: remove
    int getAttributeValueType(int index);

    int getAttributeValueData(int index);
}
