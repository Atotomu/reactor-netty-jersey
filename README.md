### reactor-netty-ext
#### 支持jersey
##### 请求方式
```
curl -X POST 
	http://localhost:8080/hot/post 
	-H 'cache-control: no-cache' 
	-H 'content-type: application/json' 
	-d '{"first_name":"li","name":"le","id":200}'
```
##### 返回结果
```
{
	"name": "keke",
	"id": 200,
	"first_name": "li"
}
```