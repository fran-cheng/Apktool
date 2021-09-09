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
import brut.androlib.err.UndefinedResObjectException;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResResSpec;

/**
 * 引用类型
 */
public class ResReferenceValue extends ResIntValue {
    /**
     * resource.arsc 里面的package
     */
    private final ResPackage mPackage;
    /**
     * 是否@引用
     */
    private final boolean mTheme;

    public ResReferenceValue(ResPackage package_, int value, String rawValue) {
        this(package_, value, rawValue, false);
    }

    public ResReferenceValue(ResPackage package_, int value, String rawValue,
                             boolean theme) {
        super(value, rawValue, "reference");
        mPackage = package_;
        mTheme = theme;
    }

    /**
     * 解码成xml
     *
     * @return String
     * @throws AndrolibException 自定义异常
     */
    @Override
    protected String encodeAsResXml() throws AndrolibException {
        if (isNull()) {
            return "@null";
        }

        ResResSpec spec = getReferent();
        if (spec == null) {
            return "@null";
        }
        boolean newId = spec.hasDefaultResource() && spec.getDefaultResource().getValue() instanceof ResIdValue;

        // generate the beginning to fix @android
//        生成初始修复@android
        String mStart = (mTheme ? '?' : '@') + (newId ? "+" : "");

        return mStart + spec.getFullName(mPackage, mTheme && spec.getType().getName().equals("attr"));
    }

    /**
     * 获取ResResSpec
     * 通过value从Package里面获取ResResSpec
     *
     * @return ResResSpec
     * @throws AndrolibException 自定义异常
     */
    public ResResSpec getReferent() throws AndrolibException {
        try {
            return mPackage.getResTable().getResSpec(getValue());
        } catch (UndefinedResObjectException ex) {
            return null;
        }
    }

    /**
     * 是否有mValue
     *
     * @return boolean
     */
    public boolean isNull() {
        return mValue == 0;
    }

    /**
     * 是否有ResResSpec
     *
     * @return boolean
     * @throws AndrolibException 自定义异常
     */
    public boolean referentIsNull() throws AndrolibException {
        return getReferent() == null;
    }
}
