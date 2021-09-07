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

/**
 * Res的ID，占32位 ，如 0x 7F 01 0000
 */
public class ResID {
    /**
     * 包ID 占 8位， 0x7F
     */
    public final int package_;
    /**
     * 类型，占8位，在package后面 如 0x7F01
     */
    public final int type;
    /**
     * ID的入口，占16位
     */
    public final int entry;

    /**
     * ID的值 为， 0xpackage__type_entry
     */
    public final int id;

    public ResID(int package_, int type, int entry) {
        this(package_, type, entry, (package_ << 24) + (type << 16) + entry);
    }

    /**
     * 通过ID来构建ResId
     *
     * @param id id
     */
    public ResID(int id) {
        this((id >> 24) & 0xff, (id >> 16) & 0x000000ff, id & 0x0000ffff, id);
    }

    public ResID(int package_, int type, int entry, int id) {
        this.package_ = (package_ == 0) ? 2 : package_;
        this.type = type;
        this.entry = entry;
        this.id = id;
    }

    @Override
    public String toString() {
//        %08x 需要使用4字节表达id 即16进制8位
//        即转换为 0xFFFFFFFF之类的格式
        return String.format("0x%08x", id);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + this.id;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ResID other = (ResID) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }
}
