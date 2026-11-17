package nc.ui.uapbd.qd.esign;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.errorlog.ErrorLogVO;
import nc.vo.bd.errorlog.ErrorMsgVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;

/** Independent, explicitly authorized native-batch transactions and their client-side receipts. */
final class QdPartnerBatchChunks {
    private static final Pattern LOG_ID=Pattern.compile("[A-Za-z0-9_-]{1,80}");
    private QdPartnerBatchChunks() { }

    interface Operation {
        void execute(String[] selected,int number,int total,Attempt attempt) throws BusinessException;
    }
    interface LogWriter {
        ErrLogResult write(List<String> messages) throws BusinessException;
    }
    interface NativeLogReader {
        String[] messageIds(String logId) throws BusinessException;
        List<ErrorMsgVO> messages(String[] ids) throws BusinessException;
    }
    static final class Attempt {
        private String requestId;private ErrLogReturnValue result;private ErrLogResult nativeErrors;
        private QdPartnerBatchRecovery.Summary recovered;
        void dispatched(String id) throws BusinessException {
            if(requestId!=null || id==null || id.trim().isEmpty()) throw new BusinessException("批改请求标识无效。");
            requestId=id;
        }
        void confirmed(ErrLogReturnValue value) throws BusinessException {
            confirmed(value,value==null?null:value.getErrLogResult());
        }
        void confirmed(ErrLogReturnValue value,ErrLogResult originalErrors) throws BusinessException {
            if(requestId==null || result!=null || recovered!=null || value==null || originalErrors==null)
                throw new BusinessException("批改结果确认状态无效。");
            result=value;nativeErrors=originalErrors;
        }
        void recovered(QdPartnerBatchRecovery.Summary value) throws BusinessException {
            if(requestId==null || result!=null || (recovered!=null && recovered.terminal())
                    || value==null || !requestId.equals(value.requestId))
                throw new BusinessException("批改恢复状态无效。");
            recovered=value;
        }
    }
    static final class Completed {
        final String requestId;final int size;final ErrLogReturnValue result;final ErrLogResult nativeErrors;final String[] selected;
        private Completed(Attempt attempt,String[] selected) {
            this.requestId=attempt.requestId;this.result=attempt.result;this.nativeErrors=attempt.nativeErrors;
            this.selected=selected.clone();this.size=selected.length;
        }
    }

    /** Validate the entire original selection before the first chunk can be dispatched. */
    static List<Completed> execute(String[] input,int maximum,Operation operation) throws BusinessException {
        if(input==null || input.length==0 || maximum<1 || operation==null) throw new BusinessException("快速批改缺少档案选择。");
        String[] selected=input.clone();Set<String> unique=new HashSet<String>();
        for(String pk:selected) if(pk==null || !pk.matches("[A-Za-z0-9_-]{1,20}") || !unique.add(pk))
            throw new BusinessException("快速批改档案主键无效或在批次间重复。");
        int total=(selected.length-1)/maximum+1,offset=0,number=0;
        List<Completed> completed=new ArrayList<Completed>();
        while(offset<selected.length) {
            int end=(int)Math.min((long)offset+maximum,selected.length);int size=end-offset;
            String[] chunk=Arrays.copyOfRange(selected,offset,end);Attempt attempt=new Attempt();number++;
            try {
                if(Thread.currentThread().isInterrupted()) throw new BusinessException("快速批改已中断。");
                operation.execute(chunk.clone(),number,total,attempt);
                requireComplete(attempt,size);
                completed.add(new Completed(attempt,chunk));
            } catch(BusinessException failure) {
                throw incomplete(completed,attempt,chunk,number,total,selected.length-end,failure);
            } catch(RuntimeException failure) {
                throw incomplete(completed,attempt,chunk,number,total,selected.length-end,failure);
            }
            offset=end;
        }
        return Collections.unmodifiableList(completed);
    }
    private static void requireComplete(Attempt attempt,int size) throws BusinessException {
        if(attempt.requestId==null || attempt.result==null || attempt.result.getTotalNum()!=size)
            throw new BusinessException("批改批次回执不完整。");
    }
    private static BusinessException incomplete(List<Completed> completed,Attempt attempt,String[] chunk,int number,int total,
            int after,Throwable cause) {
        int size=chunk.length;
        List<Completed> confirmed=new ArrayList<Completed>(completed);
        if(attempt.result!=null && attempt.requestId!=null && attempt.result.getTotalNum()==size)
            confirmed.add(new Completed(attempt,chunk));
        boolean recovered=attempt.recovered!=null && attempt.recovered.terminal();
        StringBuilder message=new StringBuilder("快速批改分批中断。已确认结果 ").append(confirmed.size()+(recovered?1:0)).append(" 批、")
            .append(covered(confirmed)+(recovered?size:0)).append(" 个档案；请重新查询界面，并按请求号核对每批的成功和失败明细。");
        if(attempt.result!=null && attempt.requestId!=null && attempt.result.getTotalNum()==size)
            message.append("第 ").append(number).append('/').append(total).append(" 批已确认，但客户端后续处理失败。");
        else if(attempt.recovered!=null)
            message.append("第 ").append(number).append('/').append(total).append(" 批：").append(attempt.recovered.describe());
        else if(attempt.requestId!=null)
            message.append("第 ").append(number).append('/').append(total).append(" 批结果未确认，请先核对请求号 ")
                .append(attempt.requestId).append("；该批 ").append(size).append(" 个档案不要直接重试。");
        else message.append("第 ").append(number).append('/').append(total).append(" 批未派发（").append(size).append(" 个档案）。");
        if(after>0) message.append("后续 ").append(after).append(" 个档案未发起。");
        if(cause!=null && cause.getMessage()!=null && !cause.getMessage().trim().isEmpty())
            message.append(attempt.recovered==null?" 原因：":" 原始回执异常：").append(cause.getMessage());
        appendIds(message,confirmed);
        return new BusinessException(message.toString(),cause);
    }
    static BusinessException mergeFailure(List<Completed> completed,Throwable cause) {
        StringBuilder message=new StringBuilder("快速批改各批次已确认完成，但汇总结果未生成；请勿重复发起，请重新查询界面并按请求号核对 ")
            .append(covered(completed)).append(" 个档案的结果。");
        if(cause!=null && cause.getMessage()!=null && !cause.getMessage().trim().isEmpty())
            message.append(" 原因：").append(cause.getMessage());
        appendIds(message,completed);return new BusinessException(message.toString(),cause);
    }
    private static int covered(List<Completed> completed) {
        int result=0;for(Completed item:completed) result+=item.size;return result;
    }
    private static void appendIds(StringBuilder text,List<Completed> completed) {
        if(completed.isEmpty()) return;text.append(" 已确认批次请求号：");
        for(int i=0;i<completed.size();i++) { if(i>0) text.append('、');text.append(completed.get(i).requestId); }
    }

    /** Combine verified receipts; an error log writer supplies a native log whose full detail remains queryable. */
    static ErrLogReturnValue combine(List<Completed> completed,Class<? extends SuperVO> type,LogWriter writer)
            throws BusinessException {
        return combine(completed,type,writer,null);
    }
    static ErrLogReturnValue combine(List<Completed> completed,Class<? extends SuperVO> type,LogWriter writer,NativeLogReader reader)
            throws BusinessException {
        if(completed==null || completed.isEmpty() || type==null || writer==null) throw new BusinessException("快速批改缺少分批结果。");
        List<SuperVO> saved=new ArrayList<SuperVO>();List<String> errors=new ArrayList<String>();
        Set<String> ids=new HashSet<String>();long total=0,failed=0;
        for(int index=0;index<completed.size();index++) {
            Completed part=completed.get(index);ErrLogReturnValue result=part==null?null:part.result;
            if(part==null || part.requestId==null || result==null || part.size!=result.getTotalNum() || result.getErrLogResult()==null)
                throw new BusinessException("快速批改分批回执不完整。");
            ErrLogResult detail=result.getErrLogResult();int failures=detail.getErrorMessagegNum();
            if(failures<0 || failures>part.size) throw new BusinessException("快速批改分批失败数量不正确。");
            total+=part.size;failed+=failures;
            if(total>Integer.MAX_VALUE || failed>Integer.MAX_VALUE) throw new BusinessException("快速批改结果数量超出范围。");
            Object raw=result.getReturnValue();if(raw!=null && !(raw instanceof SuperVO[])) throw new BusinessException("快速批改返回类型不正确。");
            Set<String> expected=new HashSet<String>(Arrays.asList(part.selected));int savedBefore=saved.size();
            if(raw!=null) for(SuperVO row:(SuperVO[])raw) {
                String pk=row==null?null:row.getPrimaryKey();
                if(row==null || row.getClass()!=type || pk==null || !expected.contains(pk) || !ids.add(pk))
                    throw new BusinessException("快速批改返回档案不属于本批、重复或类型不正确。");
                saved.add(row);
            }
            if(saved.size()-savedBefore>part.size-failures) throw new BusinessException("快速批改分批成功及失败数量不一致。");
            ErrorMsgVO[] messages=detail.getErrorMsgs();int messageCount=0;
            if(failures>0) {
                ErrorLogVO originalLog=part.nativeErrors==null?null:part.nativeErrors.getErrorLog();
                String originalId=originalLog==null?null:originalLog.getPk_errorlog();
                errors.add("第 "+(index+1)+" 批请求号："+part.requestId
                    +(originalId==null || originalId.trim().isEmpty()?"":"；原生日志号："+originalId));
                List<String> preview=texts(part.nativeErrors==null?null:part.nativeErrors.getErrorMsgs());
                List<String> normalized=texts(messages);
                if(normalized.size()<preview.size() || !normalized.subList(0,preview.size()).equals(preview))
                    throw new BusinessException("快速批改原生错误与回执不一致。");
                List<String> complete=completeNativeErrors(part.nativeErrors,preview,reader);
                errors.addAll(complete);errors.addAll(normalized.subList(preview.size(),normalized.size()));
                messageCount=complete.size()+normalized.size()-preview.size();
            }
            if(failures>0 && messageCount==0) throw new BusinessException("快速批改失败明细缺失。");
        }
        if(saved.size()>total-failed) throw new BusinessException("快速批改成功及失败数量不一致。");
        @SuppressWarnings("unchecked") SuperVO[] values=(SuperVO[])Array.newInstance(type,saved.size());
        saved.toArray(values);ErrLogResult detail;
        if(failed==0) { ErrorLogVO log=new ErrorLogVO();log.setOperationtype("快速批改");detail=new ErrLogResult(log,0,new ErrorMsgVO[0]); }
        else {
            ErrLogResult written=writer.write(Collections.unmodifiableList(errors));
            if(written==null || written.getErrorLog()==null || written.getErrorLog().getPk_errorlog()==null
                    || written.getErrorMsgs()==null) throw new BusinessException("无法核实快速批改汇总错误日志。");
            detail=new ErrLogResult(written.getErrorLog(),(int)failed,written.getErrorMsgs());
        }
        return new ErrLogReturnValue(detail,values,(int)total);
    }

    /** NC may return only an in-memory preview even though the original log contains more messages. */
    static List<String> completeNativeErrors(ErrLogResult original,List<String> preview,NativeLogReader reader)
            throws BusinessException {
        if(original==null || original.getErrorMessagegNum()<0 || preview.size()>original.getErrorMessagegNum())
            throw new BusinessException("快速批改原生错误数量不正确。");
        int count=original.getErrorMessagegNum();
        if(preview.size()==count) return preview;
        ErrorLogVO log=original.getErrorLog();String logId=log==null?null:log.getPk_errorlog();
        // NC's queryErrorMsgPksByErrorLogPk concatenates this argument into SQL.
        if(reader==null || !validLogId(logId))
            throw new BusinessException("快速批改原生错误明细不完整，无法查询原生日志。");
        String[] ids=reader.messageIds(logId);
        if(ids==null || ids.length!=count) throw new BusinessException("快速批改原生日志明细数量不一致："+logId);
        Set<String> expected=new HashSet<String>();
        for(String id:ids) if(!validLogId(id) || !expected.add(id))
            throw new BusinessException("快速批改原生日志明细标识无效："+logId);
        Map<String,String> found=new LinkedHashMap<String,String>();
        for(int start=0;start<ids.length;start+=500) {
            if(Thread.currentThread().isInterrupted()) throw new BusinessException("快速批改原生日志读取已中断："+logId);
            int end=Math.min(start+500,ids.length);String[] page=Arrays.copyOfRange(ids,start,end);
            Set<String> requested=new HashSet<String>(Arrays.asList(page));List<ErrorMsgVO> rows=reader.messages(page);
            if(rows==null || rows.size()!=page.length) throw new BusinessException("快速批改原生日志明细缺失："+logId);
            for(ErrorMsgVO row:rows) {
                String id=row==null?null:row.getPk_errormsg(),message=row==null?null:row.getErrormsg();
                if(row==null || !requested.contains(id) || !logId.equals(row.getPk_errorlog()) || message==null
                        || message.trim().isEmpty() || found.put(id,message)!=null)
                    throw new BusinessException("快速批改原生日志包含重复或其他日志的明细："+logId);
            }
        }
        List<String> complete=new ArrayList<String>();for(String id:ids) complete.add(found.get(id));
        Map<String,Integer> occurrences=new HashMap<String,Integer>();
        for(String message:complete) occurrences.put(message,Integer.valueOf(occurrences.containsKey(message)?occurrences.get(message).intValue()+1:1));
        for(String message:preview) {
            Integer remaining=occurrences.get(message);
            if(remaining==null || remaining.intValue()==0) throw new BusinessException("快速批改原生日志与回执预览不一致："+logId);
            occurrences.put(message,Integer.valueOf(remaining.intValue()-1));
        }
        return complete;
    }
    static List<String> texts(ErrorMsgVO[] messages) throws BusinessException {
        List<String> result=new ArrayList<String>();
        if(messages!=null) for(ErrorMsgVO message:messages) if(message!=null) {
            String text=message.getErrormsg();
            if(text==null || text.trim().isEmpty()) throw new BusinessException("快速批改错误明细为空。");
            result.add(text);
        }
        return result;
    }
    private static boolean validLogId(String value) {
        return value!=null && LOG_ID.matcher(value).matches();
    }
}
