package cn.edu.bookstore.model.vo;

import cn.edu.bookstore.common.util.AmapApiClient.GeocodeItem;
import lombok.Data;

import java.util.List;

/**
 * 地址自动解析返回结果。
 *
 * <p>高德可能返回多条候选地址（同音、重名、模糊匹配），前端需要弹窗让用户选择，
 * 所以 VO 把全部候选都返回，并带状态字段告知前端是单条匹配还是多条候选。
 *
 * @author 毕设项目组
 */
@Data
public class GeocodeResult {

    /** 候选地址列表（按高德置信度排序，最多 maxCandidates 条） */
    private List<Candidate> candidates;

    /** 候选数量 */
    private int count;

    /** 是否唯一匹配：true → 前端可直接回填；false → 前端需弹候选列表 */
    private boolean uniqueMatch;

    @Data
    public static class Candidate {
        /** 高德格式化完整地址，如「北京市朝阳区望京街道SOHO T1」 */
        private String formattedAddress;
        private String province;
        private String city;
        private String district;
        /** 区县 adcode（6位），省/市 adcode 可通过前2/前4位推导 */
        private String districtAdcode;

        /** 从区县 adcode 推导的省份 adcode（前2位 + 0000 填充成 6 位） */
        public String getProvinceAdcode() {
            return padAdcode(districtAdcode, 2);
        }

        /** 从区县 adcode 推导的城市 adcode（前4位 + 00 填充成 6 位） */
        public String getCityAdcode() {
            return padAdcode(districtAdcode, 4);
        }

        private static String padAdcode(String districtAdcode, int length) {
            if (districtAdcode == null || districtAdcode.isBlank() || districtAdcode.length() < length) {
                return null;
            }
            String prefix = districtAdcode.substring(0, length);
            return prefix + "0".repeat(6 - length);
        }
    }

    /** 从高德原始 GeocodeItem 转候选 DTO */
    public static Candidate from(GeocodeItem src) {
        Candidate c = new Candidate();
        c.setFormattedAddress(src.formattedAddress());
        c.setProvince(src.province());
        c.setCity(src.city());
        c.setDistrict(src.district());
        c.setDistrictAdcode(src.adcode());
        return c;
    }
}
