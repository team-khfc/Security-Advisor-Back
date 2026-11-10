package kr.securityadvisor.common.schema;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 소수 영업일이 정수로 잘려 저장되는 것을 막는다. 범위는 설정 검증에서 검사한다. */
public class BusinessDaysDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return context.reportInputMismatch(Integer.class, "영업일 수는 정수로 입력해야 합니다");
        }
        return parser.getIntValue();
    }
}
