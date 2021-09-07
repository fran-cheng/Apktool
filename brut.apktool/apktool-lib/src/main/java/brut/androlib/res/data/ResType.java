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
 * Res文件类型
 */
public class ResType {
    /**
     * Res配置标记
     */
    private final ResConfigFlags mFlags;
    /**
     * Res规格与ResResource的映射
     */
    private final Map<ResResSpec, ResResource> mResources = new LinkedHashMap<ResResSpec, ResResource>();

    public ResType(ResConfigFlags flags) {
        this.mFlags = flags;
    }

    public ResResource getResource(ResResSpec spec) throws AndrolibException {
        ResResource res = mResources.get(spec);
        if (res == null) {
            throw new UndefinedResObjectException(String.format("resource: spec=%s, config=%s", spec, this));
        }
        return res;
    }

    public ResConfigFlags getFlags() {
        return mFlags;
    }

    /**
     * 添加Res文件
     *
     * @param res ResResource
     * @throws AndrolibException
     */
    public void addResource(ResResource res) throws AndrolibException {
        addResource(res, false);
    }

    /**
     * 添加ResResource
     *
     * @param res       ResResource
     * @param overwrite 是否重写
     * @throws AndrolibException 自定义异常
     */

    public void addResource(ResResource res, boolean overwrite) throws AndrolibException {
        ResResSpec spec = res.getResSpec();
        if (mResources.put(spec, res) != null && !overwrite) {
            throw new AndrolibException(String.format("Multiple resources: spec=%s, config=%s", spec, this));
        }
    }

    @Override
    public String toString() {
        return mFlags.toString();
    }
}
