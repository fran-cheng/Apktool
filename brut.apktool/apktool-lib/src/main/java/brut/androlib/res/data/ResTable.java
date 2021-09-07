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
import brut.androlib.meta.VersionInfo;
import brut.androlib.res.AndrolibResources;
import brut.androlib.res.data.value.ResValue;

import java.util.*;

/**
 * 已经解析了的 Resources.arsc 文件的文件结构
 */
public class ResTable {
    /**
     * 用来加载框架文件
     */
    private final AndrolibResources mAndRes;

    /**
     * id与package的映射
     */
    private final Map<Integer, ResPackage> mPackagesById = new HashMap<Integer, ResPackage>();
    /**
     * name与package的映射
     */
    private final Map<String, ResPackage> mPackagesByName = new HashMap<String, ResPackage>();
    /**
     * 主要的package，解包后私有的
     */
    private final Set<ResPackage> mMainPackages = new LinkedHashSet<ResPackage>();
    /**
     * 框架packages， Android之类的安装的框架文件
     */
    private final Set<ResPackage> mFramePackages = new LinkedHashSet<ResPackage>();

    /**
     * packageRename 包名改后
     */
    private String mPackageRenamed;
    /**
     * 包名原始值
     */
    private String mPackageOriginal;
    /**
     * 包ID
     */
    private int mPackageId;
    /**
     * 分析模式
     */
    private boolean mAnalysisMode = false;
    /**
     * 共享库
     */
    private boolean mSharedLibrary = false;
    /**
     * 稀有资源
     */
    private boolean mSparseResources = false;

    /**
     * Sdk信息，写入到yml
     */
    private Map<String, String> mSdkInfo = new LinkedHashMap<>();
    /**
     * 版本信息，写入到yml
     */
    private VersionInfo mVersionInfo = new VersionInfo();

    /**
     * AndrolibResources = null
     * 用于安装框架或者公共资源
     */
    public ResTable() {
        mAndRes = null;
    }

    /**
     * 加载框架文件解码Res
     *
     * @param andRes AndrolibResources
     */
    public ResTable(AndrolibResources andRes) {
        mAndRes = andRes;
    }

    /**
     * 获取资源说明
     *
     * @param resID int
     * @return ResResSpec
     * @throws AndrolibException 自定义异常
     */
    public ResResSpec getResSpec(int resID) throws AndrolibException {
        // The pkgId is 0x00. That means a shared library is using its
        // own resource, so lie to the caller replacing with its own
        // packageId
//        pkgId为0x00。这意味着共享库正在使用
//        自己的资源，所以欺骗调用者用自己的替换
//        packageId
        if (resID >> 24 == 0) {
            int pkgId = (mPackageId == 0 ? 2 : mPackageId);
            resID = (0xFF000000 & (pkgId << 24)) | resID;
        }
        return getResSpec(new ResID(resID));
    }

    /**
     * 获取资源说明
     *
     * @param resID ResID
     * @return ResResSpec
     * @throws AndrolibException
     */
    public ResResSpec getResSpec(ResID resID) throws AndrolibException {
        return getPackage(resID.package_).getResSpec(resID);
    }

    /**
     * 主要的ResPackage  集合
     *
     * @return Set<ResPackage>
     */
    public Set<ResPackage> listMainPackages() {
        return mMainPackages;
    }

    /**
     * 框架文件ResPackage 集合
     *
     * @return Set<ResPackage>
     */
    public Set<ResPackage> listFramePackages() {
        return mFramePackages;
    }

    /**
     * 获取资源所在package
     *
     * @param id int
     * @return ResPackage
     * @throws AndrolibException 自定义异常
     */
    public ResPackage getPackage(int id) throws AndrolibException {
        ResPackage pkg = mPackagesById.get(id);
        if (pkg != null) {
            return pkg;
        }
        if (mAndRes != null) {
//            加载框架
            return mAndRes.loadFrameworkPkg(this, id, mAndRes.apkOptions.frameworkTag);
        }
        throw new UndefinedResObjectException(String.format("package: id=%d", id));
    }

    /**
     * 得到最多ResSpecCount的package， 非Android文件
     *
     * @return ResPackage
     * @throws AndrolibException 自定义异常
     */
    public ResPackage getHighestSpecPackage() throws AndrolibException {
        int id = 0;
        int value = 0;
        for (ResPackage resPackage : mPackagesById.values()) {
            if (resPackage.getResSpecCount() > value && !resPackage.getName().equalsIgnoreCase("android")) {
                value = resPackage.getResSpecCount();
                id = resPackage.getId();
            }
        }
        // if id is still 0, we only have one pkgId which is "android" -> 1
        return (id == 0) ? getPackage(1) : getPackage(id);
    }

    /**
     * 获取当前ResPackage
     *
     * @return ResPackage
     * @throws AndrolibException 自定义异常
     */
    public ResPackage getCurrentResPackage() throws AndrolibException {
        ResPackage pkg = mPackagesById.get(mPackageId);

        if (pkg != null) {
            return pkg;
        } else {
            if (mMainPackages.size() == 1) {
                return mMainPackages.iterator().next();
            }
            return getHighestSpecPackage();
        }
    }

    /**
     * 获取ResPackage，通过name
     *
     * @param name String
     * @return ResPackage
     * @throws AndrolibException 自定义异常
     */
    public ResPackage getPackage(String name) throws AndrolibException {
        ResPackage pkg = mPackagesByName.get(name);
        if (pkg == null) {
            throw new UndefinedResObjectException("package: name=" + name);
        }
        return pkg;
    }

    /**
     * 获取ResValue ， 通过 ResPackage
     *
     * @param package_ ResPackage的name
     * @param type     type
     * @param name     name
     * @return ResValue
     * @throws AndrolibException 自定义异常
     */
    public ResValue getValue(String package_, String type, String name) throws AndrolibException {
        return getPackage(package_).getType(type).getResSpec(name).getDefaultResource().getValue();
}

    /**
     * 添加Package
     *
     * @param pkg  ResPackage
     * @param main boolean
     * @throws AndrolibException 自定义异常
     */
    public void addPackage(ResPackage pkg, boolean main) throws AndrolibException {
        Integer id = pkg.getId();
        if (mPackagesById.containsKey(id)) {
            throw new AndrolibException("Multiple packages: id=" + id.toString());
        }
        String name = pkg.getName();
        if (mPackagesByName.containsKey(name)) {
            throw new AndrolibException("Multiple packages: name=" + name);
        }

        mPackagesById.put(id, pkg);
        mPackagesByName.put(name, pkg);
        if (main) {
            mMainPackages.add(pkg);
        } else {
            mFramePackages.add(pkg);
        }
    }

    /**
     * 设置分析模式
     *
     * @param mode boolean
     */
    public void setAnalysisMode(boolean mode) {
        mAnalysisMode = mode;
    }

    /**
     * 设置package重命名
     *
     * @param pkg String
     */
    public void setPackageRenamed(String pkg) {
        mPackageRenamed = pkg;
    }

    /**
     * 设置package原来的名字
     *
     * @param pkg String
     */
    public void setPackageOriginal(String pkg) {
        mPackageOriginal = pkg;
    }

    /**
     * 设置packageId
     *
     * @param id int
     */
    public void setPackageId(int id) {
        mPackageId = id;
    }

    /**
     * 设置是否共享库
     *
     * @param flag boolean
     */
    public void setSharedLibrary(boolean flag) {
        mSharedLibrary = flag;
    }

    /**
     * 设置稀有资源
     *
     * @param flag boolean
     */
    public void setSparseResources(boolean flag) {
        mSparseResources = flag;
    }

    /**
     * 清除sdk信息
     */
    public void clearSdkInfo() {
        mSdkInfo.clear();
    }

    /**
     * 添加SDK信息
     *
     * @param key   key
     * @param value value
     */
    public void addSdkInfo(String key, String value) {
        mSdkInfo.put(key, value);
    }

    /**
     * 设置版本名
     *
     * @param versionName String
     */
    public void setVersionName(String versionName) {
        mVersionInfo.versionName = versionName;
    }

    /**
     * 设置版本code
     *
     * @param versionCode String
     */
    public void setVersionCode(String versionCode) {
        mVersionInfo.versionCode = versionCode;
    }

    /**
     * 获取版本信息
     *
     * @return VersionInfo
     */
    public VersionInfo getVersionInfo() {
        return mVersionInfo;
    }

    /**
     * 获取SDK信息
     *
     * @return Map<String, String>
     */
    public Map<String, String> getSdkInfo() {
        return mSdkInfo;
    }

    /**
     * 是否分析模型
     *
     * @return boolean
     */
    public boolean getAnalysisMode() {
        return mAnalysisMode;
    }

    /**
     * 获取PackageRename
     *
     * @return String
     */
    public String getPackageRenamed() {
        return mPackageRenamed;
    }

    /**
     * 获取原始Package
     *
     * @return String
     */
    public String getPackageOriginal() {
        return mPackageOriginal;
    }

    /**
     * 获取PackageId
     *
     * @return int
     */
    public int getPackageId() {
        return mPackageId;
    }

    /**
     * 是否共享库
     *
     * @return boolean
     */
    public boolean getSharedLibrary() {
        return mSharedLibrary;
    }

    /**
     * 是否稀有资源
     *
     * @return boolean
     */
    public boolean getSparseResources() {
        return mSparseResources;
    }
}
