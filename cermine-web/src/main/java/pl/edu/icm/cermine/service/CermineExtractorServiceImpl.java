/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.edu.icm.cermine.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.edu.icm.cermine.PdfNLMContentExtractor;
import pl.edu.icm.cermine.exception.AnalysisException;

/**
 *
 * @author Aleksander Nowinski <a.nowinski@icm.edu.pl>
 */
@Component
public class CermineExtractorServiceImpl implements CermineExtractorService {

    int threadPoolSize = 4;
    Logger log = LoggerFactory.getLogger(CermineExtractorServiceImpl.class);
    List<PdfNLMContentExtractor> extractors;
    ExecutorService processingExecutor;
    @Autowired
    TaskManager taskManager;
    
    public CermineExtractorServiceImpl() {
    }

    @PostConstruct
    public void init() {
        try {
            processingExecutor = Executors.newFixedThreadPool(threadPoolSize);
            extractors = new ArrayList<PdfNLMContentExtractor>();
            for (int i = 0; i < threadPoolSize; i++) {
                extractors.add(new PdfNLMContentExtractor());
            }
        } catch (AnalysisException ex) {
            log.error("Failed to init content extractor", ex);
            throw new RuntimeException(ex);
        }
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public void setTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }
    
    

    @Override
    public ExtractionResult extractNLM(InputStream is) throws AnalysisException {
        ExtractionResult res = new ExtractionResult();
        res.setSubmit(new Date());
        Future<ExtractionResult> future = processingExecutor.submit(new SimpleExtractionCallable(is, res));
        Thread.yield();
        try {
            res = future.get();
        } catch (Exception ex) {
            log.error("Exception while executing extraction task...", ex);
            throw new RuntimeException(ex);
        }
        return res;
    }

    @Override
    public long initExtractionTask(byte[] pdf, String client) {
        ExtractionTask task = new ExtractionTask();
        task.setPdf(pdf);
        task.setClientAddress(client);
        task.setCreationDate(new Date());
        task.setStatus(ExtractionTask.TaskStatus.CREATED);
        long id = taskManager.registerTask(task);
        //now process the task...
        task.setStatus(ExtractionTask.TaskStatus.QUEUED);
        processingExecutor.submit(new ExtractingTaskExecution(task));
        
        return id;
    }
    
    

    protected PdfNLMContentExtractor obtainExtractor() {
        log.debug("Obtaining extractor from the pool");
        PdfNLMContentExtractor res = null;
        try {
            synchronized (extractors) {
                while (extractors.isEmpty()) {
                    log.debug("Extractor pool is empty, going to sleep...");
                    extractors.wait();
                }
                res = extractors.remove(0);
            }
            return res;
        } catch (InterruptedException ire) {
            log.error("Unexpected exception while waiting for extractor...", ire);
            throw new RuntimeException(ire);
        }
    }

    protected void returnExtractor(PdfNLMContentExtractor e) {
        log.debug("Returning extractor to the pool...");
        synchronized (extractors) {
            extractors.add(e);
            extractors.notify();
        }
    }

    
    
        private ExtractionResult performExtraction(ExtractionResult result, InputStream input) {
            try {
                
                PdfNLMContentExtractor e = obtainExtractor();
                result.processingStart = new Date();
                Element resEl = e.extractContent(input);
                log.debug("Extract OK.");
                XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
                String res = outputter.outputString(resEl);
                result.setNlm(res);
                result.setSucceeded(true);
                log.debug("Returning xml:\n {}", res);
            } catch (AnalysisException anal) {
                result.setError(anal);
                result.setSucceeded(false);
            } finally {
                result.setProcessingEnd(new Date());
            }
            return result;
        }
    
    private class ExtractingTaskExecution implements Runnable {
        ExtractionTask task;

        public ExtractingTaskExecution(ExtractionTask task) {
            this.task = task;
        }

        @Override
        public void run() {
            task.setStatus(ExtractionTask.TaskStatus.PROCESSING);
            ExtractionResult result = new ExtractionResult()
                    ;
            result.setProcessingStart(new Date());
            result.setSubmit(task.getCreationDate());
            performExtraction(result, new ByteArrayInputStream(task.getPdf()));
            task.setResult(result);
            task.setStatus(ExtractionTask.TaskStatus.FINISHED);
            task.setPdf(null);//clean up memory, we will overflow after few request without it...
        }
    }
    
    
    

    private class SimpleExtractionCallable implements Callable<ExtractionResult> {

        public SimpleExtractionCallable(InputStream input, ExtractionResult result) {
            this.input = input;
            this.result = result;
        }
        InputStream input;
        ExtractionResult result;

        @Override
        public ExtractionResult call() throws Exception {
            return performExtraction(result, input);
        }

    }
}
