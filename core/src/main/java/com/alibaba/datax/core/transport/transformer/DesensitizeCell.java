package com.alibaba.datax.core.transport.transformer;

/**
 * @author zhuyuandong 01383889
 * @date 2019/5/10
 */
public class DesensitizeCell {
	// PairId pairId;
	private Integer taskId; // 进行样本测试时，值为null
	private Integer algoId;
	/**
	 * 算法的类名
	 */
	private String algoClass;

	/**
	 * 算法的配置
	 */
	private String algoConf;
	/**
	 * 将要进行脱敏的原数据
	 */
	private String rawData;

	/**
	 * 脱敏是否成功
	 */
	private Boolean success;

	public DesensitizeCell() {
		success = false;
	}

	public DesensitizeCell(String algoClass, String algoConf, String rawData) {
		this.algoClass = algoClass;
		this.algoConf = algoConf;
		this.rawData = rawData;
	}

	public DesensitizeCell(String rawData) {
		this.rawData = rawData;
	}

	public Integer getTaskId() {
		return taskId;
	}

	public void setTaskId(Integer taskId) {
		this.taskId = taskId;
	}

	public Integer getAlgoId() {
		return algoId;
	}

	public void setAlgoId(Integer algoId) {
		this.algoId = algoId;
	}

	public String getAlgoClass() {
		return algoClass;
	}

	public void setAlgoClass(String algoClass) {
		this.algoClass = algoClass;
	}

	public String getAlgoConf() {
		return algoConf;
	}

	public void setAlgoConf(String algoConf) {
		this.algoConf = algoConf;
	}

	public String getRawData() {
		return rawData;
	}

	public void setRawData(String rawData) {
		this.rawData = rawData;
	}

	public Boolean getSuccess() {
		return success;
	}

	public void setSuccess(Boolean success) {
		this.success = success;
	}
}
