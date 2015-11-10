package com.taobao.text.util;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import com.taobao.text.Format;
import com.taobao.text.LineReader;
import com.taobao.text.RenderAppendable;
import com.taobao.text.ScreenBuffer;
import com.taobao.text.ScreenContext;
import com.taobao.text.Screenable;
import com.taobao.text.Style;
import com.taobao.text.ui.BorderStyle;
import com.taobao.text.ui.Element;
import com.taobao.text.ui.RowElement;
import com.taobao.text.ui.TableElement;

/**
 * 
 * @author duanling 2015年11月9日 下午11:56:03
 *
 */
public class RenderUtil {
    public static final int defaultWidth = 80;
    /**
     * 实际上这个参数通常不起作用
     */
    public static final int defaultHeight = 80;

    /**
     * 格式化输出POJO对象，可以处理继承情况和boolean字段。字段按字母顺序排序
     * 
     * @param list
     * @return
     */
    static public <O> String render(List<O> list) {
        // 获取所有的get/is的函数和字段
        List<Method> fieldMethods = new LinkedList<Method>();
        List<String> fields = new ArrayList<String>();
        Object obj = list.get(0);
        for (Method method : obj.getClass().getMethods()) {
            if (method.getDeclaringClass().equals(Object.class)) {
                continue;
            }
            String methodName = method.getName();
            int methodNameLength = methodName.length();
            if (methodNameLength > "get".length() && methodName.startsWith("get")) {
                fieldMethods.add(method);
                String field = methodName.substring("get".length());
                fields.add(field);
            }
            if (methodNameLength > "is".length() && methodName.startsWith("is")) {
                fieldMethods.add(method);
                String field = methodName.substring("is".length());
                fields.add(field);
            }
        }

        if (fieldMethods.isEmpty()) {
            return "NULL";
        }
        
        //对fields排序
        Collections.sort(fields);

        // 对method排序
        Collections.sort(fieldMethods, new Comparator<Method>() {
            @Override
            public int compare(Method m1, Method m2) {
                return m1.getName().compareTo(m2.getName());
            }
        });

        return render(list, fields, fieldMethods);
    }

    /**
     * 格式化输出POJO对象，可以处理继承情况和boolean字段。
     * 
     * @param list
     * @param fields
     *            要输出的字段列表
     * @return
     */
    static public <O> String render(List<O> list, String[] fields) {
        if (list == null || fields == null || fields.length == 0) {
            return "NULL";
        }
        if (list.size() == 0) {
            return "EMPTY LIST";
        }
        // 先据field尝试获取get/is method，如果中间有获取出错，直接抛异常
        List<Method> fieldMethods = new ArrayList<Method>(fields.length);
        Object object = list.get(0);
        for (String field : fields) {
            String uppercaseFieldName = field;
            char first = field.charAt(0);
            if (first >= 'a' && first <= 'z') {
                first += 'A' - 'a';
                uppercaseFieldName = first + field.substring(1);
            }
            Method method = null;
            try {
                method = object.getClass().getMethod("get" + uppercaseFieldName);
            } catch (Exception e) {
                // 尝试获取is函数
                try {
                    method = object.getClass().getMethod("is" + uppercaseFieldName);
                } catch (Exception ee) {
                    throw new RuntimeException("can not find get/is method!", ee);
                }
            }
            fieldMethods.add(method);
        }

        return render(list, Arrays.asList(fields), fieldMethods);
    }

    private static <O> String render(List<O> list, List<String> fields, List<Method> fieldMethods) {
        TableElement tableElement = new TableElement()
        .border(BorderStyle.DASHED)
        .separator(BorderStyle.DASHED);
        
        tableElement.row(true, fields.toArray(new String[0]));

        for (O object : list) {
            RowElement row = Element.row();
            for (Method method : fieldMethods) {
                String cell = null;
                try {
                    Object callResult = method.invoke(object);
                    if (callResult == null) {
                        cell = "null";
                    } else {
                        cell = callResult.toString();
                    }
                } catch (Exception e) {
                    cell = "exception";
                }
                row.add(Element.label(cell));
            }
            tableElement.add(row);
        }
        return render(tableElement);
    }

    /**
     * 把Element 渲染为String，默认width是80
     * 
     * @param element
     * @param width
     * @return
     */
    static public String render(final Element element) {
        return render(element, defaultWidth, defaultHeight);
    }

    /**
     * 把Element 渲染为String
     * 
     * @param element
     * @param width
     * @return
     */
    static public String render(final Element element, final int width) {
        return render(element, width, defaultHeight);
    }

    /**
     * 把Element 渲染为String
     * 
     * @param element
     * @param width
     * @param height
     * @return
     */
    static public String render(final Element element, final int width, final int height) {
        LineReader renderer = element.renderer().reader(width);
        return render(renderer, width, height);
    }

    static public String render(final LineReader renderer, final int width, final int height) {
        StringBuilder result = new StringBuilder(2048);
        while (renderer.hasLine()) {
            final ScreenBuffer buffer = new ScreenBuffer();
            renderer.renderLine(new RenderAppendable(new ScreenContext() {
                public int getWidth() {
                    return width;
                }

                public int getHeight() {
                    return height;
                }

                public Screenable append(CharSequence s) throws IOException {
                    buffer.append(s);
                    return this;
                }

                public Appendable append(char c) throws IOException {
                    buffer.append(c);
                    return this;
                }

                public Appendable append(CharSequence csq, int start, int end) throws IOException {
                    buffer.append(csq, start, end);
                    return this;
                }

                public Screenable append(Style style) throws IOException {
                    buffer.append(style);
                    return this;
                }

                public Screenable cls() throws IOException {
                    buffer.cls();
                    return this;
                }

                public void flush() throws IOException {
                    buffer.flush();
                }
            }));
            StringBuilder sb = new StringBuilder();
            try {
                buffer.format(Format.ANSI, sb);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            result.append(sb.toString()).append('\n');
        }
        return result.toString();
    }

}