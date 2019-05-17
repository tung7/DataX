package com.alibaba.datax.core.transport.transformer;

import com.alibaba.datax.common.element.*;
import com.alibaba.datax.core.statistics.communication.CommunicationTool;
import com.alibaba.datax.transformer.ComplexTransformer;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 *  "transformer": [{
 *           "name": "sf_desensitizer",  // 名称，和构造中的setTransformerName()一致
 *           "parameter": {
 *             "columnIndex": 0,  // 必填，但是我们不使用。（datax的目的是用于仅处理其中一个列，但是我们要处理多个列）
 *             "paras": ["name", "phone", "email"], // 参数，自定义，只能是String类型。
 *             "context": {                         // 上下文，自定义， 是一个Map<String,Object>,可以用来存敏感信息的关联关系。
 *               "name":{"algoId":1230,"algoArgs":""},
 *               "phone":{"algoId":1231,"algoArgs":""},
 *               "email":{"algoId":1232,"algoArgs":""}
 *             }
 *           }
 *         }],
 * @author zhuyuandong 01383889
 * @date 2019/5/8
 */
public class DesensitizeTransformer extends ComplexTransformer {

    public DesensitizeTransformer() {
        setTransformerName("sf_desensitizer");
        initAlgoDb();
    }

    public void initAlgoDb() {
//        EnvParamsConfig.getEnvConfig(null);
//        new DictionaryValues();
//        new DictionaryMapperValues();
//        new DictionaryRandomValues();
    }

    @Override
    public Record evaluate(Record record, Map<String, Object> tContext, Object... paras) {
        try {
            return evaluate2(record, tContext, paras);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Record evaluate2(Record record, Map<String, Object> tContext, Object... paras) {
        Map<String, Number> statMap = (Map<String, Number>)tContext.get("statMap");
        long startNano = System.nanoTime(); //@Tung
        try {
            statMap.put(CommunicationTool.TUNG_TEST_ALL_ITEMS, record.getColumnNumber()); // @TUng

            // paras[0]是columnIndex,这里用不到的值，需要跳过
            // todo 务必确保 colIndex和tContext中的key必须匹配，确保tContext.get不返回null
            List<Object> sensColIndexList = Arrays.asList(paras).subList(1, paras.length);

            statMap.put(CommunicationTool.TUNG_TEST_DESENSIVE_ITEMS, sensColIndexList.size());  // @TUng

            Integer taskId = (Integer) tContext.get("taskId");
            String tableName = (String) tContext.get("tableName");

            List<DesensitizeCell> cellList = new ArrayList<DesensitizeCell>();

            long sensBytes = 0L;  // @TUng

            for (Object o : sensColIndexList) {
                String colIndex = (String)o;

                // BufferedRecordTransformerExchanger.RECORD_CLASS .
                // 不知道会不会存在 TerminateRecord，DirtyRecord的情况? buildRecord后好像没有DirtyRecord创建
                Column column = record.getColumn(Integer.valueOf(colIndex));
                sensBytes += column.getByteSize();
                Object rawData = column.getRawData();
                if (rawData == null) {
                    continue;
                }

                List<Map<String, Serializable>> colConfList = (List<Map<String, Serializable>>) ((Map<String, Object>)tContext.get("algoParam")).get(colIndex);
                if (colConfList == null || colConfList.isEmpty()) {
                    // 没有对应脱敏配置，默认使用 全部屏蔽。
                } else if (colConfList.size() == 1) {
                    Map<String, Serializable> colConf = colConfList.get(0);
                    DesensitizeCell cell = new DesensitizeCell(rawData.toString());
                    cell.setTaskId(taskId);
                    cell.setAlgoId((Integer)colConf.get("algoId"));
                    cell.setAlgoConf((String)colConf.get("algoArgs")); // 优化点：只会使用一次，但是每次都传输?check是否有性能影响
                    cell.setAlgoClass((String)colConf.get("algoClass"));
                    cellList.add(cell);
                } else {
                    // 多个脱敏配置（该列数据适配多个发现规则）的情况
                }
            }

            statMap.put(CommunicationTool.TUNG_TEST_DESENSIVE_BYTES, sensBytes); // @Tung

            // 脱敏....
            try {
    //            cellList = ArithmeticEngine.process(cellList);

            } catch (Exception e) {
                e.printStackTrace();
                // 异常的话返回null?用于Commnucation统计
                return null;
    //            throw new RuntimeException(e.getMessage());
            }

            int columnSize = record.getColumnNumber();
            int desenseIdx = 0;

            int desensitiveSuccessedItems = 0;
            int desensitiveFailItems = 0;
            for (int i = 0; i < columnSize; i++) {
                // 这里按序处理，必须要求job传过来的sensColIndexList是递增的。
                if (sensColIndexList.contains(String.valueOf(i))) {
                    Column oldColumn = record.getColumn(i);
                    Column.Type type = oldColumn.getType();

                    if (oldColumn.getRawData() == null) {
                        continue;
                    }
                    DesensitizeCell desenCell = cellList.get(desenseIdx);

                    if (null == desenCell.getSuccess() || !desenCell.getSuccess()) {
                        desensitiveFailItems++;
                    } else {
                        desensitiveSuccessedItems++;
                    }
                    Object rawData = desenCell.getRawData();
                    desenseIdx++;

                    // todo 脱敏后可能类型变更, 导致无法获得newColumn。
                    Column newColumn = createNewColumn(type, rawData);

                    // 如何确保newColumn不为null
                    record.setColumn(i, newColumn);
                }
            }

            statMap.put(CommunicationTool.TUNG_TEST_DESENSIVE_SUCCEED_ITEMS, desensitiveSuccessedItems);
            statMap.put(CommunicationTool.TUNG_TEST_DESENSIVE_FAIL_ITEMS, desensitiveFailItems);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            statMap.put(CommunicationTool.TUNG_TEST_DESENSIVE_TIME, System.nanoTime() - startNano);
        }
        return record;
    }

    /**
     * return not null
     * @param type
     * @param rawData
     * @return
     */
    public Column createNewColumn(Column.Type type, Object rawData) {
        Column newColumn = null;
        try {
            switch (type) {
                case BOOL:
                    // data是Boolean
                    if (String.class.equals(rawData.getClass())) {
                        newColumn = new BoolColumn(Boolean.valueOf((String) rawData));
                    }
                    if (Boolean.class.equals(rawData.getClass())) {
                        newColumn = new BoolColumn((Boolean) rawData);
                    }
                    break;
                case DATE:
                    // mysql的datatime timestamp time都会转为Long
                    // data实际上是一个Long
//                        newColumn = new DateColumn((Long) rawData);
                    if (String.class.equals(rawData.getClass())) {
                        newColumn = new LongColumn((String) rawData);
                    }
                    if (Long.class.equals(rawData.getClass())) {
                        newColumn = new LongColumn((Long) rawData);
                    }
                    break;
                case LONG:
                    // data实际上是BigInteger
                    if (String.class.equals(rawData.getClass())) {
                        newColumn = new LongColumn((String) rawData);
                    }
                    if (BigInteger.class.equals(rawData.getClass())) {
                        newColumn = new LongColumn((BigInteger) rawData);
                    }
                    break;
                case DOUBLE:
                    // data实际上是String,但是会通过BigDecimal校验（NaN，Infinity，-Infinity则不会校验）
                    newColumn = new DoubleColumn((String) rawData);
                    break;
                case BYTES:
                    // data是byte[] todo 出现概率很小，要保证返回类型是byte[]
                    newColumn = new BytesColumn((byte[]) rawData);
                    break;
                case STRING:
                    // data就是String
                    newColumn = new StringColumn((String) rawData);
                    break;
                case NULL: //DirtyColumn,
                    // 实例化未暴露出来，todo 检查transformer中是否会传入DirtyColumn
//                        newColumn = new DirtyColumn((String)rawData);
                default:
                    // 记录异常，未识别的columnType:Column.Type
            }
        } catch (Exception e) {
            if (newColumn == null) {
                //todo
                newColumn = new StringColumn("***");
            }
        }
        return newColumn;
    }
}
