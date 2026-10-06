package org.researchagent.research.paper;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.researchagent.exception.BusinessException;
import org.researchagent.exception.ErrorCode;
import org.researchagent.research.LocalWorkspace;
import org.researchagent.research.knowledge.KnowledgeService;
import org.researchagent.research.project.ResearchProjectService;
import org.researchagent.research.task.TaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class PaperToCodeService {
    public record Method(long id, String title, String source, String methodBrief, int textLength) { }
    private static final int MAX_PDF_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT = 120_000;
    private static final Pattern SPACE = Pattern.compile("[\\t ]+");
    private static final Pattern METHOD_HEADING = Pattern.compile("(?im)^\\s*(?:[0-9]+(?:\\.[0-9]+)*[. ]*)?(?:method(?:ology)?|approach|proposed method|model architecture|experimental setup|implementation details|方法|模型结构|实验设置)\\s*[:：]?\\s*$");
    private final JdbcTemplate jdbc;
    private final ResearchProjectService projects;
    private final KnowledgeService knowledge;
    private final TaskRepository tasks;

    public PaperToCodeService(JdbcTemplate jdbc, ResearchProjectService projects, KnowledgeService knowledge, TaskRepository tasks) {
        this.jdbc = jdbc; this.projects = projects; this.knowledge = knowledge; this.tasks = tasks;
    }

    @Transactional
    public Method importPdf(long projectId, String title, MultipartFile file) throws IOException {
        projects.getOwnedProject(LocalWorkspace.ID, projectId);
        String name = title == null ? "" : title.strip();
        if (name.isBlank() || name.length() > 500) throw new BusinessException(ErrorCode.PARAMS_ERROR, "论文标题需为 1–500 字符");
        if (file == null || file.isEmpty() || file.getSize() > MAX_PDF_BYTES)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择不超过 10 MB 的 PDF");
        byte[] bytes = file.getBytes();
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-')
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不是 PDF");
        String text;
        try (var pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() > 100)
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "暂不支持加密或超过 100 页的 PDF");
            text = new PDFTextStripper().getText(pdf);
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(ErrorCode.PARAMS_ERROR, "PDF 无法解析；扫描版请先进行 OCR"); }
        text = SPACE.matcher(text).replaceAll(" ").replace("\r", "").strip();
        if (text.length() < 200) throw new BusinessException(ErrorCode.PARAMS_ERROR, "可提取文本不足 200 字符；扫描版请先进行 OCR");
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
        String brief = brief(text);
        String source = "PDF: " + name;
        String savedText = text;
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into paper_method(project_id,title,source,extracted_text,method_brief) values(?,?,?,?,?)", new String[]{"id"});
            statement.setLong(1, projectId); statement.setString(2, name); statement.setString(3, source);
            statement.setString(4, savedText); statement.setString(5, brief); return statement;
        }, key);
        // The retrieval index is rebuilt from this durable document in the knowledge module.
        knowledge.add(projectId, source.substring(0, Math.min(240, source.length())), text.substring(0, Math.min(20_000, text.length())));
        return new Method(key.getKey().longValue(), name, source, brief, text.length());
    }

    public List<Method> list(long projectId) {
        projects.getOwnedProject(LocalWorkspace.ID, projectId);
        return jdbc.query("select id,title,source,method_brief,length(extracted_text) as text_length from paper_method where project_id=? order by id desc limit 100",
                (r,n) -> new Method(r.getLong("id"), r.getString("title"), r.getString("source"), r.getString("method_brief"), r.getInt("text_length")), projectId);
    }

    public Method get(long projectId, long id) {
        return list(projectId).stream().filter(method -> method.id() == id).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "论文方法记录不存在"));
    }

    @Transactional
    public long createTask(long projectId, long methodId, String instructions, String provider) {
        var project = projects.getOwnedProject(LocalWorkspace.ID, projectId);
        Method method = get(projectId, methodId);
        String extra = instructions == null ? "" : instructions.strip();
        if (extra.length() > 4000) throw new BusinessException(ErrorCode.PARAMS_ERROR, "补充要求不能超过 4000 字符");
        String prompt = "依据项目知识库中的论文《" + method.title() + "》实现可复现的研究代码。\n"
                + "来源：" + method.source() + "\n目标语言：" + project.defaultLanguage() + "\n"
                + "请先核对方法、数据、损失函数、训练设置和评估指标；缺失信息用 TODO 标出，不得编造论文结论。"
                + "输出实现代码、依赖说明、运行命令、最小验证方式和未解决问题。\n"
                + "方法提纲（用户应先审阅）：\n" + method.methodBrief() + "\n补充要求：" + extra;
        return tasks.create(projectId, LocalWorkspace.ID, prompt, provider);
    }

    static String brief(String text) {
        var matcher = METHOD_HEADING.matcher(text);
        String section = matcher.find() ? text.substring(matcher.end(), Math.min(text.length(), matcher.end() + 5000)) : text.substring(0, Math.min(text.length(), 3000));
        section = section.strip();
        int references = section.toLowerCase(Locale.ROOT).indexOf("\nreferences\n");
        if (references >= 0) section = section.substring(0, references);
        return "自动抽取的原文片段，尚未由模型验证方法或公式；请结合 PDF 人工核对。\n\n" + section;
    }
}
