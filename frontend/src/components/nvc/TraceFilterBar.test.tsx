import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import TraceFilterBar from './TraceFilterBar';

describe('TraceFilterBar', () => {
  it('渲染所有筛选条件', () => {
    const onSearch = vi.fn();
    render(<TraceFilterBar onSearch={onSearch} />);

    // 验证所有筛选条件存在
    expect(screen.getByText('会话 ID（conversationId）')).toBeInTheDocument();
    expect(screen.getByText('开始时间')).toBeInTheDocument();
    expect(screen.getByText('结束时间')).toBeInTheDocument();
    expect(screen.getByText('状态')).toBeInTheDocument();
    expect(screen.getByText('工具名')).toBeInTheDocument();
    expect(screen.getByText('Span 类型')).toBeInTheDocument();
  });

  it('点击查询按钮调用 onSearch', () => {
    const onSearch = vi.fn();
    render(<TraceFilterBar onSearch={onSearch} />);

    // 点击查询按钮
    fireEvent.click(screen.getByText('查询'));

    // 验证 onSearch 被调用
    expect(onSearch).toHaveBeenCalledWith({});
  });

  it('填写会话 ID 后查询', () => {
    const onSearch = vi.fn();
    render(<TraceFilterBar onSearch={onSearch} />);

    // 填写会话 ID
    const sessionInput = screen.getByPlaceholderText('留空查询所有');
    fireEvent.change(sessionInput, { target: { value: '123' } });

    // 点击查询
    fireEvent.click(screen.getByText('查询'));

    // 验证 onSearch 参数
    expect(onSearch).toHaveBeenCalledWith({ sessionId: '123' });
  });

  it('选择状态后查询', () => {
    const onSearch = vi.fn();
    render(<TraceFilterBar onSearch={onSearch} />);

    // 选择状态
    const statusSelect = screen.getByDisplayValue('全部');
    fireEvent.change(statusSelect, { target: { value: 'FAILED' } });

    // 点击查询
    fireEvent.click(screen.getByText('查询'));

    // 验证 onSearch 参数
    expect(onSearch).toHaveBeenCalledWith({ status: 'FAILED' });
  });

  it('填写工具名后查询', () => {
    const onSearch = vi.fn();
    render(<TraceFilterBar onSearch={onSearch} />);

    // 填写工具名
    const toolNameInput = screen.getByPlaceholderText('如: profile_update');
    fireEvent.change(toolNameInput, { target: { value: 'profile_update' } });

    // 点击查询
    fireEvent.click(screen.getByText('查询'));

    // 验证 onSearch 参数
    expect(onSearch).toHaveBeenCalledWith({ toolName: 'profile_update' });
  });

  it('选择 Span 类型后查询', () => {
    const onSearch = vi.fn();
    render(<TraceFilterBar onSearch={onSearch} />);

    // 选择 Span 类型
    const spanTypeSelect = screen.getByDisplayValue('全部 Span');
    fireEvent.change(spanTypeSelect, { target: { value: 'TOOL_CALL' } });

    // 点击查询
    fireEvent.click(screen.getByText('查询'));

    // 验证 onSearch 参数
    expect(onSearch).toHaveBeenCalledWith({ spanType: 'TOOL_CALL' });
  });

  it('组合多个筛选条件', () => {
    const onSearch = vi.fn();
    render(<TraceFilterBar onSearch={onSearch} />);

    // 填写多个条件
    const sessionInput = screen.getByPlaceholderText('留空查询所有');
    fireEvent.change(sessionInput, { target: { value: '123' } });

    const toolNameInput = screen.getByPlaceholderText('如: profile_update');
    fireEvent.change(toolNameInput, { target: { value: 'profile_update' } });

    const statusSelect = screen.getByDisplayValue('全部');
    fireEvent.change(statusSelect, { target: { value: 'SUCCESS' } });

    // 点击查询
    fireEvent.click(screen.getByText('查询'));

    // 验证 onSearch 参数包含所有条件
    expect(onSearch).toHaveBeenCalledWith({
      sessionId: '123',
      toolName: 'profile_update',
      status: 'SUCCESS',
    });
  });

  it('点击重置按钮清空所有筛选条件', () => {
    const onSearch = vi.fn();
    render(<TraceFilterBar onSearch={onSearch} />);

    // 填写一些条件
    const sessionInput = screen.getByPlaceholderText('留空查询所有');
    fireEvent.change(sessionInput, { target: { value: '123' } });

    const toolNameInput = screen.getByPlaceholderText('如: profile_update');
    fireEvent.change(toolNameInput, { target: { value: 'profile_update' } });

    // 点击重置
    fireEvent.click(screen.getByText('重置'));

    // 验证 onSearch 被调用（空参数）
    expect(onSearch).toHaveBeenCalledWith({});

    // 验证输入框被清空
    expect(sessionInput).toHaveValue('');
    expect(toolNameInput).toHaveValue('');
  });

  it('空值不传入 onSearch 参数', () => {
    const onSearch = vi.fn();
    render(<TraceFilterBar onSearch={onSearch} />);

    // 填写后清空
    const toolNameInput = screen.getByPlaceholderText('如: profile_update');
    fireEvent.change(toolNameInput, { target: { value: 'test' } });
    fireEvent.change(toolNameInput, { target: { value: '' } });

    // 点击查询
    fireEvent.click(screen.getByText('查询'));

    // 验证空值不传入
    expect(onSearch).toHaveBeenCalledWith({});
  });
});
