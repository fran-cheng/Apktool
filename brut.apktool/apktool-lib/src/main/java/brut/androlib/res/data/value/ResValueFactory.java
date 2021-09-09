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

import android.util.TypedValue;
import brut.androlib.AndrolibException;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResTypeSpec;
import brut.util.Duo;

/**
 * ResValue工厂
 */
public class ResValueFactory {
    /**
     * Resource.arsc 下的package
     */
    private final ResPackage mPackage;

    public ResValueFactory(ResPackage package_) {
        this.mPackage = package_;
    }

    /**
     * 创建 ResScalarValue
     *
     * @param type     类型
     * @param value    value
     * @param rawValue rawValue
     * @return ResScalarValue
     * @throws AndrolibException 自定义异常
     */
    public ResScalarValue factory(int type, int value, String rawValue) throws AndrolibException {
        switch (type) {
            case TypedValue.TYPE_NULL:
//                null 类型
//                特殊情况$empty作为显式定义的空值
                if (value == TypedValue.DATA_NULL_UNDEFINED) { // Special case $empty as explicitly defined empty value
                    return new ResStringValue(null, value);
                } else if (value == TypedValue.DATA_NULL_EMPTY) {
                    return new ResEmptyValue(value, rawValue, type);
                }
//                reference
                return new ResReferenceValue(mPackage, 0, null);
            case TypedValue.TYPE_REFERENCE:
//                reference
//                引用类型
                return newReference(value, null);
            case TypedValue.TYPE_ATTRIBUTE:
            case TypedValue.TYPE_DYNAMIC_ATTRIBUTE:
//                类型属性|动态属性类型
                return newReference(value, rawValue, true);
            case TypedValue.TYPE_STRING:
//                字符串类型
                return new ResStringValue(rawValue, value);
            case TypedValue.TYPE_FLOAT:
//                浮点型
                return new ResFloatValue(Float.intBitsToFloat(value), value, rawValue);
            case TypedValue.TYPE_DIMENSION:
//                dimens
                return new ResDimenValue(value, rawValue);
            case TypedValue.TYPE_FRACTION:
//                 分数
                return new ResFractionValue(value, rawValue);
            case TypedValue.TYPE_INT_BOOLEAN:
//                boolean
                return new ResBoolValue(value != 0, value, rawValue);
            case TypedValue.TYPE_DYNAMIC_REFERENCE:
//                类型动态引用
                return newReference(value, rawValue);
        }

        if (type >= TypedValue.TYPE_FIRST_COLOR_INT && type <= TypedValue.TYPE_LAST_COLOR_INT) {
//            颜色类型
            return new ResColorValue(value, rawValue);
        }
        if (type >= TypedValue.TYPE_FIRST_INT && type <= TypedValue.TYPE_LAST_INT) {
//            int
            return new ResIntValue(value, rawValue, type);
        }

//        抛出无效类型异常
        throw new AndrolibException("Invalid value type: " + type);
    }

    /**
     * 创建 ResIntBasedValue
     *
     * @param value    String  路径
     * @param rawValue int  值
     * @return ResIntBasedValue
     */
    public ResIntBasedValue factory(String value, int rawValue) {
        if (value == null) {
            return new ResFileValue("", rawValue);
        }
        if (value.startsWith("res/")) {
            return new ResFileValue(value, rawValue);
        }
        if (value.startsWith("r/") || value.startsWith("R/")) { //AndroResGuard
            return new ResFileValue(value, rawValue);
        }
        return new ResStringValue(value, rawValue);
    }

    /**
     * 包装工厂
     *
     * @param parent      parent  资源ID
     * @param items       items
     * @param resTypeSpec ResTypeSpec
     * @return ResBagValue
     * @throws AndrolibException 自定义异常
     */
    public ResBagValue bagFactory(int parent, Duo<Integer, ResScalarValue>[] items, ResTypeSpec resTypeSpec) throws AndrolibException {
//        引用类型
        ResReferenceValue parentVal = newReference(parent, null);

        if (items.length == 0) {
            return new ResBagValue(parentVal);
        }
        int key = items[0].m1;
        if (key == ResAttr.BAG_KEY_ATTR_TYPE) {
//            包键属性类型
            return ResAttr.factory(parentVal, items, this, mPackage);
        }

//        类型
        String resTypeName = resTypeSpec.getName();

        // Android O Preview added an unknown enum for c. This is hardcoded as 0 for now.
//        Android O预览为c添加了一个未知的enum。目前硬编码为0。
        if (ResTypeSpec.RES_TYPE_NAME_ARRAY.equals(resTypeName)
            || key == ResArrayValue.BAG_KEY_ARRAY_START || key == 0) {
            return new ResArrayValue(parentVal, items);
        }

        if (ResTypeSpec.RES_TYPE_NAME_PLURALS.equals(resTypeName) ||
//            PLURALS
            (key >= ResPluralsValue.BAG_KEY_PLURALS_START && key <= ResPluralsValue.BAG_KEY_PLURALS_END)) {
            return new ResPluralsValue(parentVal, items);
        }

        if (ResTypeSpec.RES_TYPE_NAME_STYLES.equals(resTypeName)) {
//            Res类型名称样式
            return new ResStyleValue(parentVal, items, this);
        }

        if (ResTypeSpec.RES_TYPE_NAME_ATTR.equals(resTypeName)) {
//            类型名称属性
            return new ResAttr(parentVal, 0, null, null, null);
        }

//        不能包装这个类型异常
        throw new AndrolibException("unsupported res type name for bags. Found: " + resTypeName);
    }

    /**
     * 引用类型
     *
     * @param resID    resID
     * @param rawValue rawValue
     * @return ResReferenceValue
     */
    public ResReferenceValue newReference(int resID, String rawValue) {
        return newReference(resID, rawValue, false);
    }

    /**
     * 引用类型
     *
     * @param resID    resID
     * @param rawValue rawValue
     * @param theme    theme
     * @return ResReferenceValue
     */
    public ResReferenceValue newReference(int resID, String rawValue, boolean theme) {
        return new ResReferenceValue(mPackage, resID, rawValue, theme);
    }
}
