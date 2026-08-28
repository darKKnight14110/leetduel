package com.leetduel.problem.problem;

import com.leetduel.problem.dto.CreateProblemRequest;
import com.leetduel.problem.dto.InternalProblemDetailDto;
import com.leetduel.problem.dto.InternalRandomProblemDto;
import com.leetduel.problem.dto.ParameterDto;
import com.leetduel.problem.dto.ProblemDetailDto;
import com.leetduel.problem.dto.ProblemCatalogDto;
import com.leetduel.problem.dto.ProblemImportRequest;
import com.leetduel.problem.dto.ProblemSummaryDto;
import com.leetduel.problem.dto.TestCaseDto;
import com.leetduel.problem.exception.ProblemNotFoundException;
import com.leetduel.problem.signature.FunctionSignature;
import com.leetduel.problem.signature.FunctionSignatureRepository;
import com.leetduel.problem.signature.LanguageStub;
import com.leetduel.problem.signature.LanguageStubRepository;
import com.leetduel.problem.signature.Parameter;
import com.leetduel.problem.signature.ParameterRepository;
import com.leetduel.problem.tag.ProblemTag;
import com.leetduel.problem.tag.ProblemTagRepository;
import com.leetduel.problem.tag.Tag;
import com.leetduel.problem.tag.TagRepository;
import com.leetduel.problem.testcase.TestCase;
import com.leetduel.problem.testcase.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final TagRepository tagRepository;
    private final ProblemTagRepository problemTagRepository;
    private final TestCaseRepository testCaseRepository;
    private final FunctionSignatureRepository functionSignatureRepository;
    private final ParameterRepository parameterRepository;
    private final LanguageStubRepository languageStubRepository;

    public Page<ProblemSummaryDto> listProblems(Difficulty difficulty, String tagName, Pageable pageable) {
        return problemRepository.search(difficulty, tagName, pageable)
                .map(p -> new ProblemSummaryDto(p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty()));
    }

    public List<ProblemSummaryDto> getSummaries(List<UUID> problemIds) {
        return problemRepository.findAllById(problemIds).stream()
                .map(p -> new ProblemSummaryDto(p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty()))
                .toList();
    }

    public Page<ProblemCatalogDto> getCatalog(Pageable pageable) {
        Page<Problem> problems = problemRepository.findAll(pageable);
        List<UUID> ids = problems.getContent().stream().map(Problem::getId).toList();
        Map<UUID, List<String>> tagsByProblem = new LinkedHashMap<>();
        ids.forEach(id -> tagsByProblem.put(id, new ArrayList<>()));
        if (!ids.isEmpty()) {
            for (Object[] row : problemTagRepository.findTagNamesByProblemIds(ids)) {
                UUID problemId = (UUID) row[0];
                tagsByProblem.computeIfAbsent(problemId, ignored -> new ArrayList<>()).add((String) row[1]);
            }
        }
        return problems.map(problem -> new ProblemCatalogDto(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getDescription(), problem.getDifficulty(), tagsByProblem.getOrDefault(problem.getId(), List.of())));
    }

    public ProblemDetailDto getPublicDetail(UUID problemId) {
        Problem problem = requireProblem(problemId);
        FunctionSignature signature = functionSignatureRepository.findByProblemId(problemId)
                .orElseThrow(() -> new ProblemNotFoundException("Problem has no function signature: " + problemId));
        List<ParameterDto> parameters = toParameterDtos(signature.getId());
        Map<String, String> stubs = new HashMap<>();
        for (LanguageStub stub : languageStubRepository.findByProblemId(problemId)) {
            stubs.put(stub.getLanguage(), stub.getStubCode());
        }
        List<TestCaseDto> sampleCases = new ArrayList<>();
        for (TestCase tc : testCaseRepository.findByProblemIdAndIsSampleTrueOrderByOrdinalAsc(problemId)) {
            sampleCases.add(new TestCaseDto(tc.getOrdinal(), tc.getInput(), tc.getExpectedOutput(), true));
        }

        return new ProblemDetailDto(
                problem.getId(), problem.getSlug(), problem.getTitle(), problem.getDescription(),
                problem.getDifficulty(), signature.getFunctionName(), signature.getReturnType(),
                parameters, stubs, sampleCases);
    }

    public InternalProblemDetailDto getInternalDetail(UUID problemId) {
        Problem problem = requireProblem(problemId);
        FunctionSignature signature = functionSignatureRepository.findByProblemId(problemId)
                .orElseThrow(() -> new ProblemNotFoundException("Problem has no function signature: " + problemId));
        List<ParameterDto> parameters = toParameterDtos(signature.getId());
        List<TestCaseDto> allCases = new ArrayList<>();
        for (TestCase tc : testCaseRepository.findByProblemIdOrderByOrdinalAsc(problemId)) {
            allCases.add(new TestCaseDto(tc.getOrdinal(), tc.getInput(), tc.getExpectedOutput(), tc.isSample()));
        }

        return new InternalProblemDetailDto(
                problem.getId(), signature.getFunctionName(), signature.getReturnType(),
                parameters, problem.getTimeLimitMs(), problem.getMemoryLimitMb(), allCases);
    }

    @Transactional
    public UUID createProblem(CreateProblemRequest request) {
        return createProblem(request, null, null);
    }

    @Transactional
    public UUID importProblem(ProblemImportRequest request) {
        return problemRepository.findBySourceAndSourceId(request.source(), request.sourceId())
                .map(Problem::getId)
                .orElseGet(() -> createProblem(request.problem(), request.source(), request.sourceId()));
    }

    private UUID createProblem(CreateProblemRequest request, String source, String sourceId) {
        Problem problem = new Problem();
        problem.setSlug(request.slug());
        problem.setTitle(request.title());
        problem.setDescription(request.description());
        problem.setDifficulty(request.difficulty());
        problem.setSource(source);
        problem.setSourceId(sourceId);
        if (request.timeLimitMs() != null) {
            problem.setTimeLimitMs(request.timeLimitMs());
        }
        if (request.memoryLimitMb() != null) {
            problem.setMemoryLimitMb(request.memoryLimitMb());
        }
        problem = problemRepository.save(problem);

        if (request.tags() != null) {
            for (String tagName : request.tags()) {
                Tag tag = tagRepository.findByName(tagName).orElseGet(() -> {
                    Tag t = new Tag();
                    t.setName(tagName);
                    return tagRepository.save(t);
                });
                problemTagRepository.save(new ProblemTag(problem.getId(), tag.getId()));
            }
        }

        FunctionSignature signature = new FunctionSignature();
        signature.setProblemId(problem.getId());
        signature.setFunctionName(request.functionName());
        signature.setReturnType(request.returnType());
        signature = functionSignatureRepository.save(signature);

        for (int i = 0; i < request.parameters().size(); i++) {
            ParameterDto paramDto = request.parameters().get(i);
            Parameter parameter = new Parameter();
            parameter.setFunctionSignatureId(signature.getId());
            parameter.setOrdinal(i);
            parameter.setName(paramDto.name());
            parameter.setType(paramDto.type());
            parameterRepository.save(parameter);
        }

        for (Map.Entry<String, String> entry : request.languageStubs().entrySet()) {
            LanguageStub stub = new LanguageStub();
            stub.setProblemId(problem.getId());
            stub.setLanguage(entry.getKey());
            stub.setStubCode(entry.getValue());
            languageStubRepository.save(stub);
        }

        for (int i = 0; i < request.testCases().size(); i++) {
            CreateProblemRequest.TestCaseRequest tcReq = request.testCases().get(i);
            TestCase testCase = new TestCase();
            testCase.setProblemId(problem.getId());
            testCase.setOrdinal(i);
            testCase.setInput(tcReq.input());
            testCase.setExpectedOutput(tcReq.expectedOutput());
            testCase.setSample(tcReq.isSample());
            testCaseRepository.save(testCase);
        }

        return problem.getId();
    }

    // Backs matchmaking-service's per-match problem selection - see
    // ProblemRepository.findRandom() for the scale caveat on this query.
    public InternalRandomProblemDto getRandomProblem() {
        Problem problem = problemRepository.findRandom()
                .orElseThrow(() -> new ProblemNotFoundException("No problems exist to select from"));
        return new InternalRandomProblemDto(problem.getId());
    }

    public void deleteProblem(UUID problemId) {
        requireProblem(problemId);
        problemRepository.deleteById(problemId);
    }

    private Problem requireProblem(UUID problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException("Problem not found: " + problemId));
    }

    private List<ParameterDto> toParameterDtos(UUID signatureId) {
        List<ParameterDto> result = new ArrayList<>();
        for (Parameter p : parameterRepository.findByFunctionSignatureIdOrderByOrdinalAsc(signatureId)) {
            result.add(new ParameterDto(p.getName(), p.getType()));
        }
        return result;
    }
}
