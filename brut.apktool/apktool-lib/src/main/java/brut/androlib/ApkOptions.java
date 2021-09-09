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
package brut.androlib;

import java.util.Collection;

/**
 * 打包选项 ==> 命令行
 */
public class ApkOptions {
    public boolean forceBuildAll = false;
    public boolean forceDeleteFramework = false;
    public boolean debugMode = false;
    /**
     * 详细日志
     */
    public boolean verbose = false;
    public boolean copyOriginalFiles = false;
    public boolean updateFiles = false;
    public boolean isFramework = false;
    public boolean resourcesAreCompressed = false;
    public boolean useAapt2 = false;
    /**
     * 在构建步骤中禁用资源文件的处理。
     * //            停用 PNG 处理。
     * //如果您已处理 PNG 文件，或者要创建不需要减小文件大小的调试 build，则可使用此选项。启用此选项可以加快执行速度，但会增大输出文件大小。
     */
    public boolean noCrunch = false;
    public int forceApi = 0;
    public Collection<String> doNotCompress;

    public String frameworkFolderLocation = null;
    public String frameworkTag = null;
    public String aaptPath = "";

    public int aaptVersion = 1; // default to v1

    public boolean isAapt2() {
        return this.useAapt2 || this.aaptVersion == 2;
    }
}
