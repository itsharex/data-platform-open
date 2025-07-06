
package cn.dataplatform.open.support.service.impl;


import cn.dataplatform.open.support.service.WorkspaceService;
import cn.dataplatform.open.support.store.entity.Workspace;
import cn.dataplatform.open.support.store.mapper.WorkspaceMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WorkspaceServiceImpl extends ServiceImpl<WorkspaceMapper, Workspace> implements WorkspaceService {
}