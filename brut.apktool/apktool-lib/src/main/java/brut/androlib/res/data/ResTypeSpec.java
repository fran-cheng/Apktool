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
package brut.androlib.res.data;

import brut.androlib.AndrolibException;
import brut.androlib.err.UndefinedResObjectException;

import java.util.*;

/**
 * 资源类型规格
 * 资源文件的名字
 */
public final class ResTypeSpec {

    public static final String RES_TYPE_NAME_ARRAY = "array";
    public static final String RES_TYPE_NAME_PLURALS = "plurals";
    public static final String RES_TYPE_NAME_STYLES = "style";
    public static final String RES_TYPE_NAME_ATTR = "attr";

    /**
     * 类型名字
     */
    private final String mName;

    /**
     * 资源类型 与 资源ID的ResResSpec映射
     */
    private final Map<String, ResResSpec> mResSpecs = new LinkedHashMap<String, ResResSpec>();

    private final ResTable mResTable;
    private final ResPackage mPackage;
    /**
     * 此块所持有的类型标识符
     */
    private final int mId;
    private final int mEntryCount;

    /**
     * 读取 ResTable_typeSpec
     * 类型 anim ,array ,attr之类的
     *
     * @param name       根据下标拿到的值 如 anim ,array ,attr
     * @param resTable   ResTable
     * @param package_   包名相关
     * @param id         此块所持有的类型标识符
     * @param entryCount uint32_t条目配置掩码的数量
     */
    public ResTypeSpec(String name, ResTable resTable, ResPackage package_, int id, int entryCount) {
        this.mName = name;
        this.mResTable = resTable;
        this.mPackage = package_;
        this.mId = id;
        this.mEntryCount = entryCount;
    }

    /**
     * 获得type名
     * 资源文件的名字
     *
     * @return String
     */
    public String getName() {
        return mName;
    }

    /**
     * 此块所持有的类型标识符
     * 类型ID值
     *
     * @return int
     */
    public int getId() {
        return mId;
    }

    /**
     * 类型名是否是string
     *
     * @return boolean
     */
    public boolean isString() {
        return mName.equalsIgnoreCase("string");
    }

    /**
     * 获取ResResSpec
     *
     * @param name 资源名字
     * @return ResResSpec
     */
    public ResResSpec getResSpec(String name) throws AndrolibException {
        ResResSpec spec = getResSpecUnsafe(name);
        if (spec == null) {
            throw new UndefinedResObjectException(String.format("resource spec: %s/%s", getName(), name));
        }
        return spec;
    }

    /**
     * 获取ResResSpec
     *
     * @param name 资源名字
     * @return ResResSpec
     */
    public ResResSpec getResSpecUnsafe(String name) {
        return mResSpecs.get(name);
    }

    /**
     * 移除ResSpec 通过 ResResSpec
     *
     * @param spec ResResSpec
     */
    public void removeResSpec(ResResSpec spec) {
        mResSpecs.remove(spec.getName());
    }

    /**
     * 添加ResSpec 通过 ResResSpec
     *
     * @param spec ResResSpec
     */
    public void addResSpec(ResResSpec spec) throws AndrolibException {
        if (mResSpecs.put(spec.getName(), spec) != null) {
//            已存在则报异常
            throw new AndrolibException(String.format("Multiple res specs: %s/%s", getName(), spec.getName()));
        }
    }

    @Override
    public String toString() {
        return mName;
    }
}
