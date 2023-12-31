package com.nhndooray.samplespringbatch.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class WeeklyStatStepConfig {

    private final DataSource dataSource;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    private static final Integer PAGE_SIZE = 3;
    private static final Integer CHUNK_SIZE = 3;

    @Bean
    public Step aggregateWeeklyStatStep() throws Exception {
        return new StepBuilder("aggregateWeeklyStatStep", jobRepository)
                .chunk(CHUNK_SIZE, transactionManager)
                .transactionManager(transactionManager)
                .reader(aggregatedWeeklyStatReader(null, null))
                .processor(ctrProcessor())
                .writer(weeklyStatWriter())
                .build();
    }

    // TODO - 04
    //    - JobParameter 의 값을 각각 beginDate, endDate 에 주입합니다.
    //    - JobParameter 의 값을 정상적으로 주입하려면 특별한 Scope 가 필요합니다.
    @Bean
    @StepScope
    public JdbcPagingItemReader<WeeklyStat> aggregatedWeeklyStatReader(@Value("#jobParameters[beginDate]") String beginDate,
                                                                       @Value("#jobParameters[endDate]")  String endDate) throws Exception {
        Map<String, Object> paramValues = Map.of(
                "beginDate", beginDate,
                "endDate", endDate);

        return new JdbcPagingItemReaderBuilder<WeeklyStat>()
                .pageSize(PAGE_SIZE)
                .fetchSize(PAGE_SIZE)
                .dataSource(dataSource)
                .rowMapper(new BeanPropertyRowMapper<>(WeeklyStat.class))
                .queryProvider(createQueryProvider())
                .parameterValues(paramValues)
                .name("aggregatedWeeklyStatReader")
                .build();
    }

    @Bean
    public PagingQueryProvider createQueryProvider() throws Exception {
        var query = new SqlPagingQueryProviderFactoryBean();
        query.setDataSource(dataSource);
        query.setSelectClause("min(stat_date) as stat_date, item_id, sum(click_count) as click_count, sum(expose_count) as expose_count ");
        query.setFromClause("from STAT_DAILY");
        query.setWhereClause("where stat_date >= :beginDate and stat_date <= :endDate");
        query.setGroupClause("group by item_id");
        query.setSortKeys(Map.of("stat_date", Order.ASCENDING, "item_id", Order.ASCENDING));

        return query.getObject();
    }

    @Bean
    public ItemProcessor ctrProcessor() {
        return new CtrProcessor();
    }

    @Bean
    public ItemWriter<WeeklyStat> weeklyStatWriter() {

        String sql = "insert into STAT_WEEKLY(STAT_DATE, ITEM_ID, CLICK_COUNT,EXPOSE_COUNT, CTR) "
                     + "values "
                     + "(:statDate, :itemId, :clickCount, :exposeCount, :ctr)";

        return new JdbcBatchItemWriterBuilder<WeeklyStat>()
                .dataSource(dataSource)
                .sql(sql)
                .beanMapped()
                .build();
    }

}
