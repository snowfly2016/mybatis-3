package mybatis;

import java.util.List;
import java.util.Map;

public interface UserMapper {

  public List<Map<String,Object>> selectAll(String name, String age);
}
