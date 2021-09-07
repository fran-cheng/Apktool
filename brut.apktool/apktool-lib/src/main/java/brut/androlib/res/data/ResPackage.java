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
import brut.androlib.res.data.value.ResFileValue;
import brut.androlib.res.data.value.ResValueFactory;
import brut.androlib.res.xml.ResValuesXmlSerializable;
import brut.util.Duo;

import java.util.*;
import java.util.logging.Logger;

/**
 * ResPackage  ， resource.arsc 里面的package
 */
public class ResPackage {
    /**
     * 整个 resource.arsc 文件
     */
    private final ResTable mResTable;
    /**
     * package的ID ， 一般默认的私有包都为 0x7f
     */
    private final int mId;
    /**
     * 包名
     */
    private final String mName;
    /**
     * 资源ID，ResResSpec
     */
    private final Map<ResID, ResResSpec> mResSpecs = new LinkedHashMap<ResID, ResResSpec>();
    /**
     * 资源配置标记，资源类型
     */
    private final Map<ResConfigFlags, ResType> mConfigs = new LinkedHashMap<ResConfigFlags, ResType>();
    /**
     * 资源类型名，与资源类型说明
     */
    private final Map<String, ResTypeSpec> mTypes = new LinkedHashMap<String, ResTypeSpec>();
    /**
     * 搜集的唯一的ResId
     */
    private final Set<ResID> mSynthesizedRes = new HashSet<ResID>();

    /**
     * res/value 工厂
     */
    private ResValueFactory mValueFactory;

    /**
     * ResPackage
     * resource.arsc 里面的package
     *
     * @param resTable ResTable ，resource.arsc
     * @param id       包ID，  私有==> 0x7F
     * @param name     包名
     */
    public ResPackage(ResTable resTable, int id, String name) {
        this.mResTable = resTable;
        this.mId = id;
        this.mName = name;
    }

    /**
     * 资源（规格）说明 集合
     *
     * @return List<ResResSpec>
     */
    public List<ResResSpec> listResSpecs() {
        return new ArrayList<ResResSpec>(mResSpecs.values());
    }

    /**
     * 是否已有此规格的资源，通过ResID
     *
     * @param resID ResID
     * @return boolean
     */
    public boolean hasResSpec(ResID resID) {
        return mResSpecs.containsKey(resID);
    }

    /**
     * 获取资源的规格，通过ResID
     *
     * @param resID ResID
     * @return ResResSpec
     * @throws UndefinedResObjectException 自定义异常
     */
    public ResResSpec getResSpec(ResID resID) throws UndefinedResObjectException {
        ResResSpec spec = mResSpecs.get(resID);
        if (spec == null) {
            throw new UndefinedResObjectException("resource spec: " + resID.toString());
        }
        return spec;
    }

    /**
     * 获取资源规格数
     *
     * @return int
     */
    public int getResSpecCount() {
        return mResSpecs.size();
    }

    /**
     * 获取或创建Config
     *
     * @param flags ResConfigFlags
     * @return ResType
     */
    public ResType getOrCreateConfig(ResConfigFlags flags) {
        ResType config = mConfigs.get(flags);
        if (config == null) {
            config = new ResType(flags);
            mConfigs.put(flags, config);
        }
        return config;
    }

    /**
     * 获取资源类型规格，通过资源类型名
     *
     * @param typeName 资源类型名
     * @return ResTypeSpec 资源类型规格
     * @throws AndrolibException 自定义异常
     */
    public ResTypeSpec getType(String typeName) throws AndrolibException {
        ResTypeSpec type = mTypes.get(typeName);
        if (type == null) {
            throw new UndefinedResObjectException("type: " + typeName);
        }
        return type;
    }

    /**
     * 获取Res下的文件夹集合
     *
     * @return Set<ResResource>
     */
    public Set<ResResource> listFiles() {
        Set<ResResource> ret = new HashSet<ResResource>();
//         从资源规格说明，遍历ResResource的value，路径
        for (ResResSpec spec : mResSpecs.values()) {
            for (ResResource res : spec.listResources()) {
                if (res.getValue() instanceof ResFileValue) {
                    ret.add(res);
                }
            }
        }
        return ret;
    }

    /**
     * 获取res/values下的xml文件
     *
     * @return Collection<ResValuesFile>
     */
    public Collection<ResValuesFile> listValuesFiles() {
        Map<Duo<ResTypeSpec, ResType>, ResValuesFile> ret = new HashMap<Duo<ResTypeSpec, ResType>, ResValuesFile>();
        for (ResResSpec spec : mResSpecs.values()) {
            for (ResResource res : spec.listResources()) {
                if (res.getValue() instanceof ResValuesXmlSerializable) {
                    ResTypeSpec type = res.getResSpec().getType();
                    ResType config = res.getConfig();
                    Duo<ResTypeSpec, ResType> key = new Duo<ResTypeSpec, ResType>(type, config);
                    ResValuesFile values = ret.get(key);
                    if (values == null) {
                        values = new ResValuesFile(this, type, config);
                        ret.put(key, values);
                    }
                    values.addResource(res);
                }
            }
        }
        return ret.values();
    }

    /**
     * 获取 ResTable
     *
     * @return ResTable
     */
    public ResTable getResTable() {
        return mResTable;
    }

    /**
     * 获取 包ID
     *
     * @return packageId
     */
    public int getId() {
        return mId;
    }

    /**
     * 获取包名
     *
     * @return packageName
     */
    public String getName() {
        return mName;
    }

    /**
     * 唯一ID里面是否已经包含了
     *
     * @param resId ResID
     * @return boolean
     */
    boolean isSynthesized(ResID resId) {
        return mSynthesizedRes.contains(resId);
    }

    /**
     * 移除 ResSpec 资源规格
     *
     * @param spec ResResSpec
     */
    public void removeResSpec(ResResSpec spec) {
        mResSpecs.remove(spec.getId());
    }

    /**
     * 添加 ResSpec资源规格
     *
     * @param spec ResResSpec
     * @throws AndrolibException 自定义异常
     */
    public void addResSpec(ResResSpec spec) throws AndrolibException {
        if (mResSpecs.put(spec.getId(), spec) != null) {
            throw new AndrolibException("Multiple resource specs: " + spec);
        }
    }

    /**
     * 添加资源类型
     *
     * @param type ResTypeSpec
     */
    public void addType(ResTypeSpec type) {
        if (mTypes.containsKey(type.getName())) {
            LOGGER.warning("Multiple types detected! " + type + " ignored!");
        } else {
            mTypes.put(type.getName(), type);
        }
    }

    /**
     * 添加ResId
     *
     * @param resId resId
     */
    public void addSynthesizedRes(int resId) {
        mSynthesizedRes.add(new ResID(resId));
    }

    @Override
    public String toString() {
        return mName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ResPackage other = (ResPackage) obj;
        if (this.mResTable != other.mResTable && (this.mResTable == null || !this.mResTable.equals(other.mResTable))) {
            return false;
        }
        if (this.mId != other.mId) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + (this.mResTable != null ? this.mResTable.hashCode() : 0);
        hash = 31 * hash + this.mId;
        return hash;
    }

    /**
     * 获取Value工厂
     *
     * @return ResValueFactory
     */
    public ResValueFactory getValueFactory() {
        if (mValueFactory == null) {
            mValueFactory = new ResValueFactory(this);
        }
        return mValueFactory;
    }

    private final static Logger LOGGER = Logger.getLogger(ResPackage.class.getName());
}
