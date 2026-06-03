package com.ruleforge.console.util;

import com.ruleforge.console.entity.FileVersionEntity;

/**
 * 版本号处理工具类
 */
public final class VersionUtils {

    // 私有构造函数，防止实例化
    private VersionUtils() {
    }

    /**
     * 递增文件版本号。
     * 实现类似语义化版本（Semantic Versioning）的递增，但每段最大值为 99。
     * 例如：1.0.0 -> 1.0.1, 1.0.99 -> 1.1.0, 1.99.99 -> 2.0.0
     *
     * @param currentVersionEntity 当前最新的版本实体 (可为 null，表示从 1.0.0 开始)
     * @param newVersionEntity     需要设置新版本号的版本实体
     * @return 设置了新版本号的版本实体 (与 newVersionEntity 是同一个对象)
     */
    public static FileVersionEntity incrementVersionFileVersion(FileVersionEntity currentVersionEntity, FileVersionEntity newVersionEntity) {
        if (currentVersionEntity == null || currentVersionEntity.getVersionNum() == null || currentVersionEntity.getVersionNum().isEmpty()) {
            newVersionEntity.setVersionNum("1.0.0");
            newVersionEntity.setVersionNumReal(1000_000L); // 1 * 1_000_000 + 0 * 1_000 + 0
            return newVersionEntity;
        }
        newVersionEntity.setVersionNum(incrementVersion(currentVersionEntity.getVersionNum()));
        newVersionEntity.setVersionNumReal(convertVersionToLong(newVersionEntity.getVersionNum()));
        return newVersionEntity;
    }

    public static String incrementVersion(String currentVersion) {
        // 假设版本号格式为 X.Y.Z
        String[] parts = currentVersion.split("\\.");
        int[] nums;

        // 处理不规范的版本号，至少保证有三段
        if (parts.length < 3) {
            nums = new int[3];
            for (int i = 0; i < parts.length; i++) {
                try {
                    nums[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    // 如果解析失败，则从 1.0.0 开始
                    return "1.0.0";
                }
            }
            // 如果原始版本是 1 或 1.1，则下一个版本是 2.0.0 或 1.2.0，这里统一按最后一位递增处理，不足补0
            // 为了简化，这里直接将不足三位的视为从 1.0.0 或 2.0.0 开始递增可能更合理，或者严格要求输入格式
            // 按照原逻辑，如果输入 "1"，parts=["1"], nums=[1,0,0] -> 递增后 1.0.1
            // 如果输入 "1.1", parts=["1","1"], nums=[1,1,0] -> 递增后 1.1.1
            // 这里我们稍微调整，如果不足三位，认为下一个版本是 2.0.0 (如果第一位是1) 或 (第一位+1).0.0
            if (parts.length == 1) {
                nums = new int[]{nums[0] + 1, 0, 0}; // 例如 1 -> 2.0.0
            } else { // parts.length == 2
                nums = new int[]{nums[0], nums[1] + 1, 0}; // 例如 1.1 -> 1.2.0
                // 处理进位
                if (nums[1] >= 100) {
                    nums[1] = 0;
                    nums[0]++;
                }
            }

        } else {
            nums = new int[3]; // 只处理前三段
            try {
                for (int i = 0; i < 3; i++) {
                    nums[i] = Integer.parseInt(parts[i]);
                }
            } catch (NumberFormatException e) {
                // 如果解析失败，则从 1.0.0 开始
                return "1.0.0";
            }

            // 从最后一位开始递增并处理进位
            int idx = 2; // Z 位
            nums[idx]++;
            while (nums[idx] >= 100 && idx > 0) { // 每段最大99
                nums[idx] = 0;
                nums[--idx]++; // 向前进位
            }
            // 最高位 X 可以超过 99
        }

        return String.valueOf(nums[0]) + '.' + nums[1] + '.' + nums[2];
    }

    /**
     * 将字符串版本号转换为 long 型表示，便于排序和比较。
     * 格式 X.Y.Z -> X * 1_000_000 + Y * 1_000 + Z
     * "latest" 特殊处理为 1L (或根据需要调整)。
     *
     * @param version 版本号字符串
     * @return long 型表示的版本号
     */
    public static Long convertVersionToLong(String version) {
        if (version == null || version.isEmpty() || "latest".equalsIgnoreCase(version)) {
            // "latest" 通常表示最新快照，数值上可以设为最大或最小，或一个特殊值
            // 原代码中似乎将其视为 1L，这里保持一致，但可能需要根据业务调整
            return 1L;
        }

        String[] parts = version.split("\\.");
        long versionReal = 0L;
        try {
            if (parts.length >= 1) {
                versionReal += Long.parseLong(parts[0]) * 1000_000L;
            }
            if (parts.length >= 2) {
                versionReal += Long.parseLong(parts[1]) * 1000L;
            }
            if (parts.length >= 3) {
                versionReal += Long.parseLong(parts[2]);
            }
        } catch (NumberFormatException e) {
            // 处理无效格式，可以返回 0 或抛出异常
            return 0L;
        }
        return versionReal;
    }
}