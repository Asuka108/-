import sys
sys.path.insert(0, '/var/www/after-sales-robot/backend')
from services.ai_service import get_ai_response

# Test with agent role history
history = [{"role": "user", "content": "耳机坏了"}, {"role": "agent", "content": "请描述具体问题"}]
result = get_ai_response("还是连不上", history, "", "你是一个客服助手，请简短回复")
print("Result:", repr(result[:80]))
