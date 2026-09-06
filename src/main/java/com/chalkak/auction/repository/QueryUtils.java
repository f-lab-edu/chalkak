package com.chalkak.auction.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.StringPath;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class QueryUtils {

  public static BooleanExpression containsIgnoreCase(StringPath path, String value) {
    if (!StringUtils.hasText(value)) {
      return Expressions.TRUE;
    }
    return path.containsIgnoreCase(value);
  }

  public static <T> BooleanExpression equalsIfNotNull(SimpleExpression<T> path, T value) {
    return Objects.isNull(value) ? Expressions.TRUE : path.eq(value);
  }

}
