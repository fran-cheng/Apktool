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
package brut.androlib.meta;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * apktool.yml 文件内容
 * 记录相关信息，回编的时候使用
 */
public class MetaInfo {
    /**
     * ApkTool版本
     */
    public String version;
    /**
     * 输入的APK文件名
     */
    public String apkFileName;
    /**
     * 是否是框架文件，回编的时候使用
     */
    public boolean isFrameworkApk;
    /**
     * 使用的框架
     */
    public UsesFramework usesFramework;
    /**
     * SDK 信息
     */
    public Map<String, String> sdkInfo;
    /**
     * 包信息
     */
    public PackageInfo packageInfo;
    /**
     * 版本信息
     */
    public VersionInfo versionInfo;
    /**
     * 资源压缩
     */
    public boolean compressionType;
    /**
     * 共享库
     */
    public boolean sharedLibrary;
    /**
     * 稀有资源
     */
    public boolean sparseResources;
    /**
     * 未知文件
     */
    public Map<String, String> unknownFiles;
    /**
     * 搜集的不压缩的文件
     */
    public Collection<String> doNotCompress;

    /**
     * 构建Yaml
     *
     * @return Yaml
     */
    private static Yaml getYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        StringExRepresent representer = new StringExRepresent();
        PropertyUtils propertyUtils = representer.getPropertyUtils();
        propertyUtils.setSkipMissingProperties(true);

        return new Yaml(new StringExConstructor(), representer, options);
    }

    /**
     * 保存YAML文件
     *
     * @param output Writer
     */
    public void save(Writer output) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        getYaml().dump(this, output);
    }

    /**
     * 保存YAML文件
     *
     * @param file File
     */
    public void save(File file) throws IOException {
        try (
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            Writer writer = new BufferedWriter(outputStreamWriter)
        ) {
            save(writer);
        }
    }

    /**
     * 加载YAML文件
     *
     * @param is InputStream
     * @return MetaInfo
     */
    public static MetaInfo load(InputStream is) {
        return getYaml().loadAs(is, MetaInfo.class);
    }
}
